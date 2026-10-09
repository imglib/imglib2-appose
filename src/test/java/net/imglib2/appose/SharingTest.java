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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Tests sending ImgLib2 images between processes, via {@link ImgLib2Codec}.
 */
public class SharingTest
{
	private static Service groovy;

	@BeforeAll
	public static void setUp() throws Exception
	{
		// Pass our same classpath to the Groovy worker, so that it encodes
		// and decodes ImgLib2 images the same way.
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

	/** An {@code ArrayImg} input arrives as an {@code NDArray} of the worker's own. */
	@Test
	public void arrayImgToWorker() throws Exception
	{
		final Img< FloatType > img = ArrayImgs.floats( new float[] { 0, 1, 2, 3, 4, 5 }, 3, 2 );
		final Task task = groovy.task(
			"import net.imglib2.appose.ShmImg\n" +
			"[img.shm().managed(), img.shape().toIntArray() as List, new ShmImg(img).collect { it.get() }]",
			Collections.singletonMap( "img", img ) ).waitFor();
		assertSame( TaskStatus.COMPLETE, task.status, task.error );
		assertEquals( "[true, [2, 3], [0.0, 1.0, 2.0, 3.0, 4.0, 5.0]]", String.valueOf( task.result() ) );
	}

	/** An {@code ArrayImg} output arrives as an {@code NDArray} of the service's own. */
	@Test
	public void arrayImgFromWorker() throws Exception
	{
		Task task = groovy.task(
			"import net.imglib2.img.array.ArrayImgs\n" +
			"def img = ArrayImgs.ints(3, 2)\n" +
			"int i = 0\n" +
			"img.forEach { it.set(10 * i++) }\n" +
			"img" ).waitFor();
		assertSame( TaskStatus.COMPLETE, task.status, task.error );
		assertInstanceOf( NDArray.class, task.result() );
		NDArray ndArray = ( NDArray ) task.result();
		assertInstanceOf( SharedMemoryView.class, ndArray.shm() );
		assertTrue( ( ( SharedMemoryView ) ndArray.shm() ).managed() );
		final String name = ndArray.shm().name();

		assertImgValues( new ShmImg<>( ndArray ) );

		// Once both processes are done with the image, the service frees it.
		assertTrue( shmExists( name ) );
		ndArray = null;
		task = null;
		assertTrue( eventually( () -> {
			gcWorker();
			return !shmExists( name );
		} ) );
	}

	// NB: A separate method, so that no reference to the image lingers in the caller.
	private static void assertImgValues( final ShmImg< IntType > img )
	{
		assertEquals( 3, img.dimension( 0 ) );
		assertEquals( 2, img.dimension( 1 ) );
		int i = 0;
		for ( final IntType t : img )
			assertEquals( 10 * i++, t.get() );
	}

	/** A {@code ShmImg} is shared by reference, so the worker's writes are visible. */
	@Test
	public void shmImgShared() throws Exception
	{
		try ( final ShmImg< FloatType > img = new ShmImg<>( new FloatType(), 4, 3 ) )
		{
			final Task task = groovy.task(
				"import net.imglib2.appose.ShmImg\n" +
				"new ShmImg(img).forEach { it.set(7) }\n" +
				"img.shm() instanceof org.apposed.appose.SharedMemoryView",
				Collections.singletonMap( "img", img ) ).waitFor();
			assertSame( TaskStatus.COMPLETE, task.status, task.error );
			assertEquals( false, task.result() );
			for ( final FloatType t : img )
				assertEquals( 7, t.get() );
		}
	}

	/**
	 * A worker writes labels into a managed image sent to it by the service, which
	 * sees them in place, with no copying.
	 */
	@Test
	public void managedOutputImage() throws Exception
	{
		try ( final ShmImg< IntType > labels = ShmImg.managed( new IntType(), 4, 2 ) )
		{
			final Task task = groovy.task(
				"import net.imglib2.appose.ShmImg\n" +
				"def view = new ShmImg(labels)\n" +
				"def c = view.localizingCursor()\n" +
				"while (c.hasNext()) { c.fwd(); c.get().set(c.getIntPosition(1) + 1) }\n" +
				"labels.shm().name()",
				Collections.singletonMap( "labels", labels ) ).waitFor();
			assertSame( TaskStatus.COMPLETE, task.status, task.error );
			assertEquals( labels.ndArray().shm().name(), task.result() );
			final net.imglib2.Cursor< IntType > c = labels.localizingCursor();
			while ( c.hasNext() )
			{
				c.fwd();
				assertEquals( c.getIntPosition( 1 ) + 1, c.get().get() );
			}
		}
	}

	/** An image with no matching {@code DType} is sent as a proxy, as before. */
	@Test
	public void unsupportedTypeProxied() throws Exception
	{
		final Img< ARGBType > img = ArrayImgs.argbs( 2, 2 );
		final Task task = groovy.task( "img.getClass().simpleName",
			Collections.singletonMap( "img", img ) ).waitFor();
		assertSame( TaskStatus.COMPLETE, task.status, task.error );
		assertEquals( "ServiceProxy", task.result() );

		// NB: ImgLib2 has no shared memory access for booleans yet.
		final Task boolTask = groovy.task( "img.getClass().simpleName",
			Collections.singletonMap( "img", ArrayImgs.booleans( 2, 2 ) ) ).waitFor();
		assertSame( TaskStatus.COMPLETE, boolTask.status, boolTask.error );
		assertEquals( "ServiceProxy", boolTask.result() );
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
