/*-
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2024 - 2025 Tobias Pietzsch and Curtis Rueden.
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

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;
import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests shared access to N-dimensional images between processes via Appose.
 */
public class ShmImgTest
{
	private static Service python;

	@BeforeAll
	public static void setUp() throws IOException
	{
		// Read environment.yml from test resources.
		InputStream is = ShmImgTest.class.getResourceAsStream( "environment.yml" );
		assertNotNull( is );
		String envYaml = new BufferedReader( new InputStreamReader( is, StandardCharsets.UTF_8 ) )
			.lines().collect( Collectors.joining( "\n" ) );

		// Build an environment with Python + Appose + NumPy available.
		// We build it beneath the target folder, rather than polluting ~/.local/share/appose.
		File envDir = Paths.get("target").resolve("envs").resolve("imglib2-appose-test").toFile().getAbsoluteFile();
		python = Appose.include( envYaml, "environment.yml" ).logDebug().build(envDir).python();
	}

	@AfterAll
	public static void tearDown()
	{
		if ( python.isAlive() )
			python.close();
	}

	/**
	 * We create an {@link NDArray} then wrap it into an {@code ArrayImg}.
	 * <p>
	 * We keep track of the {@code NDArray} so that we can pass it to Appose.
	 * </p>
	 */
	@Test
	public void ndArrayToPython() throws Exception
	{
		final FloatType type = new FloatType();
		try ( final NDArray ndArray = NDArrays.ndArray( type, 4, 3, 2 ) )
		{
			final Img< FloatType > img = NDArrays.asArrayImg( ndArray, type );

			int i = 0;
			for ( FloatType t : img )
				t.set( i++ );

			assertAccessibleFromPython( ndArray );
		}
	}

	/**
	 * We create a {@link ShmImg}, which creates and wraps an
	 * {@link NDArray} internally.
	 * <p>
	 * We refer to it as a {@code ShmImg} (rather than a plain
	 * {@code Img}) so that we can invoke the {@code .ndArray()} method for
	 * passing the wrapped {@code NDArray} to Appose.
	 * </p>
	 */
	@Test
	public void shmImgToPython() throws Exception
	{
		try ( final ShmImg< FloatType > img = new ShmImg<>( new FloatType(), 4, 3, 2 ) )
		{
			int i = 0;
			for ( FloatType t : img )
				t.set( i++ );

			assertAccessibleFromPython( img.ndArray() );
		}
	}

	/**
	 * We create a {@link ShmImg}, which creates and wraps an
	 * {@link NDArray} internally.
	 * <p>
	 * We use it as {@link Img} and extract its {@link NDArray} using
	 * {@link NDArrays#asNDArray} so that we can pass it to Appose.
	 * </p>
	 */
	@Test
	public void implicitShmImgToPython() throws Exception
	{
		final Img< FloatType > img = new ShmImg<>( new FloatType(), 4, 3, 2 );

		int i = 0;
		for ( FloatType t : img )
			t.set( i++ );

		assertAccessibleFromPython( NDArrays.asNDArray( img, false ) );
	}

	/**
	 * We create a "normal" ArrayImg (or any other
	 * {@link RandomAccessibleInterval}) not backed by shared memory, then copy it
	 * into an {@link NDArray} using {@link NDArrays#asNDArray} so that we can
	 * pass it to Appose.
	 * <p>
	 * This works with {@link ShmImg} and other
	 * {@link RandomAccessibleInterval}s, both. It either extracts the wrapped
	 * {@link NDArray}, or copies into a new one.
	 * </p>
	 */
	@Test
	public void nonShmImgToPython() throws Exception
	{
		final Img< FloatType > img = ArrayImgs.floats( 4, 3, 2 );

		int i = 0;
		for ( FloatType t : img )
			t.set( i++ );

		assertAccessibleFromPython( NDArrays.asNDArray( img ) );
	}

	/**
	 * Creates an {@link Img}, passes it through Appose as an NDArray, wrap it as a
	 * ShmImg on the other end.
	 */
	@Test
	public void groovy() throws Exception
	{
		final Img< FloatType > img = ArrayImgs.floats(
			new float[] {
				0, 1, 2,
				3, 4, 5 },
			3, 2 );

		// Pass our same classpath to the Groovy worker.
		final List< String > classpath = Arrays.asList(
			System.getProperty( "java.class.path" ).split( "[:;]" )
		);
		Environment env = Appose.system();
		try ( Service service = env.groovy( classpath ) )
		{
			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "ndarray", NDArrays.asNDArray( img ) );
			String script =
				"import net.imglib2.appose.ShmImg\n" +
				"ShmImg img = new ShmImg(ndarray)\n" +
				"img.collect { it.get() }\n";
			Task task = service.task( script, inputs );
			task.waitFor();
			assertSame( TaskStatus.COMPLETE, task.status, task.error );
			Object result = task.outputs.get( "result" );
			assertInstanceOf( List.class, result );
			List< ? > values = ( List< ? > ) result;
			for ( int i = 0; i < values.size(); i++ )
			{
				Object value = values.get( i );
				assertInstanceOf( Number.class, value );
				Number number = ( Number ) value;
				assertEquals( i, number.floatValue() );
			}
		}
	}

	private void assertAccessibleFromPython( final NDArray data ) throws IOException, InterruptedException
	{
		final String printInput =
			"import numpy as np\n" +
			"task.outputs['datatype'] = str(type(data))\n" +
			"task.outputs['result'] = str(data.ndarray())";

		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "data", data );

		Task task = python.task( printInput, inputs );
		task.waitFor();

		assertSame( TaskStatus.COMPLETE, task.status, task.error );
		Object dataType = task.outputs.get( "datatype" );
		assertEquals( "<class 'appose.types.NDArray'>", dataType );
		String result = ( String ) task.outputs.get( "result" );
		List< String > actual = Arrays.asList( result.split( "(\r\n|\n|\r)" ) );
		List< String > expected = Arrays.asList(
			"[[[ 0.  1.  2.  3.]",
			"  [ 4.  5.  6.  7.]",
			"  [ 8.  9. 10. 11.]]",
			"",
			" [[12. 13. 14. 15.]",
			"  [16. 17. 18. 19.]",
			"  [20. 21. 22. 23.]]]"
		);
		assertEquals( expected, actual );
	}
}
