/*-
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2024 - 2026 Tobias Pietzsch and Curtis Rueden.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package net.imglib2.appose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.apposed.appose.Appose;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.apposed.appose.SharedMemoryView;
import org.apposed.appose.util.Platforms;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.jna.Library;
import com.sun.jna.Native;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.integer.ShortType;

/**
 * Tests {@link ShmCellImgs}.
 */
public class ShmCellImgsTest
{
	private static Service groovy;

	@BeforeAll
	public static void setUp() throws Exception
	{
		// Pass our same classpath to the Groovy worker, so that it decodes
		// ImgLib2 images the same way.
		final List< String > classpath = Arrays.asList(
			System.getProperty( "java.class.path" ).split( "[:;]" ) );
		groovy = Appose.system().groovy( classpath );
	}

	@AfterAll
	public static void tearDown()
	{
		if ( groovy.isAlive() )
			groovy.close();
	}

	/**
	 * Cells are copied into managed shared memory, which workers share in
	 * place, and which is freed once neither process uses the image anymore.
	 */
	@Test
	public void managedCells() throws Exception
	{
		final Set< String > names = shareCells();
		assertTrue( names.stream().allMatch( ShmCellImgsTest::shmExists ) );
		assertTrue( eventually( () -> {
			gcWorker();
			return names.stream().noneMatch( ShmCellImgsTest::shmExists );
		} ) );
	}

	/**
	 * Creates a {@code ShmCellImg}, checks its pixels, and sends one cell to
	 * the worker.
	 *
	 * @return The names of the shared memory blocks holding the cells.
	 */
	// NB: A separate method, so that no reference to the image lingers in the caller.
	private static Set< String > shareCells() throws Exception
	{
		final CellImg< ShortType, ? > source = new CellImgFactory<>( new ShortType(), 16 ).create( 50, 40, 20 );
		final Cursor< ShortType > c = source.localizingCursor();
		while ( c.hasNext() )
		{
			c.fwd();
			c.get().set( ( short ) value( c.getIntPosition( 0 ), c.getIntPosition( 1 ), c.getIntPosition( 2 ) ) );
		}

		final List< NDArray > cells = Collections.synchronizedList( new ArrayList<>() );
		final Img< ShortType > img = ShmCellImgs.createShmCellImg( source, ( dType, shape ) -> {
			final NDArray ndArray = NDArray.managed( dType, shape );
			cells.add( ndArray );
			return ndArray;
		} );

		final Cursor< ShortType > sc = source.localizingCursor();
		final RandomAccess< ShortType > ra = img.randomAccess();
		while ( sc.hasNext() )
		{
			sc.fwd();
			assertEquals( sc.get().get(), ra.setPositionAndGet( sc ).get() );
		}
		assertEquals( 4 * 3 * 2, cells.size() );

		final NDArray cell = cells.get( 0 );
		assertTrue( ( ( SharedMemoryView ) cell.shm() ).managed() );
		final Task task = groovy.task(
			"import net.imglib2.appose.ShmImg\n" +
			"def ra = new ShmImg(cell).randomAccess()\n" +
			"[cell.shm().managed(), cell.shape().toIntArray() as List, ra.setPositionAndGet(3, 2, 1).get()]",
			Collections.singletonMap( "cell", cell ) ).waitFor();
		assertSame( TaskStatus.COMPLETE, task.status, task.error );
		assertEquals( "[true, [16, 16, 16], " + value( 3, 2, 1 ) + "]", String.valueOf( task.result() ) );

		final Set< String > names = new HashSet<>();
		for ( final NDArray ndArray : cells )
			names.add( ndArray.shm().name() );
		return names;
	}

	private static int value( final int x, final int y, final int z )
	{
		return x + 100 * y + 7 * z;
	}

	/** Runs the garbage collector in the worker. */
	private static void gcWorker()
	{
		try
		{
			groovy.task( "System.gc()" ).waitFor();
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}
	}

	/**
	 * Checks whether the named shared memory block exists, without attaching
	 * to it. On Windows, where this cannot be checked, always returns true.
	 */
	private static boolean shmExists( final String name )
	{
		if ( Platforms.isWindows() )
			return true;
		final int fd = LibC.INSTANCE.shm_open( "/" + name, 0, 0 ); // O_RDONLY
		if ( fd < 0 )
			return false;
		LibC.INSTANCE.close( fd );
		return true;
	}

	private interface LibC extends Library
	{
		LibC INSTANCE = Native.load( "c", LibC.class );

		int shm_open( String name, int oflag, int mode );

		int close( int fd );
	}

	/** Waits up to 10 seconds for the condition, running the garbage collector meanwhile. */
	private static boolean eventually( final BooleanSupplier condition ) throws InterruptedException
	{
		final long deadline = System.currentTimeMillis() + 10_000;
		while ( !condition.getAsBoolean() )
		{
			if ( System.currentTimeMillis() > deadline )
				return false;
			System.gc();
			Thread.sleep( 20 );
		}
		return true;
	}
}
