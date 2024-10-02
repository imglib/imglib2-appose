/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 Appose developers.
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
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;
import org.apposed.appose.Appose;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static net.imglib2.appose.NDArrayUtils.asNDArray;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Create an Img, pass it through Appose, and print it in Python as NumPy ndarray.
 * <p>
 * Various possible variants. Not sure all of them are good ideas.
 * </p>
 */
public class SharedMemoryImgTest
{
	private static final String PRINT_INPUT =
			"import numpy as np\n" +
			"task.outputs['result'] = str(img.ndarray())";

	private static Service python;

	@BeforeAll
	public static void setUp() throws IOException
	{
		// Read environment.yml from test resources.
		InputStream is = SharedMemoryImgTest.class.getResourceAsStream("environment.yml");
		assertNotNull( is );
		String envYaml = new BufferedReader( new InputStreamReader(is, StandardCharsets.UTF_8) )
			.lines().collect( Collectors.joining("\n") );

		// Build an environment with Python + Appose + NumPy available.
		python = Appose.include( envYaml, "environment.yml" ).logDebug().build().python();
	}

	@AfterAll
	public static void tearDown()
	{
		if ( python.isAlive() )
			python.close();
	}

	/**
	 * We create a NDArray then wrap it into an ArrayImg.
	 * <p>
	 * We have to keep track of the NDArray to pass it to Appose.
	 * </p>
	 */
	@Test
	public void version1() throws Exception
	{
		final FloatType type = new FloatType();
		try ( final NDArray ndArray = NDArrayUtils.ndArray( type, 4, 3, 2 ) )
		{
			final Img< FloatType > img = NDArrayUtils.asArrayImg( ndArray, type );

			int i = 0;
			for ( FloatType t : img )
				t.set( i++ );

			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "img", ndArray );
			List< String > actual = doTask( python, PRINT_INPUT, inputs );
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

	/**
	 * We create SharedMemoryImg, which creates and wraps a NDArray internally.
	 * <p>
	 * Less cumbersome, but we need to refer to it as SharedMemoryImg to be able
	 * to get the .ndArray() out for passing it to Appose.
	 * </p>
	 */
	@Test
	public void version2() throws Exception
	{
		final SharedMemoryImg< FloatType > img = new SharedMemoryImg<>( new FloatType(), 4, 3, 2 );

		int i = 0;
		for (FloatType t : img)
			t.set(i++);

		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "img", img.ndArray());
		doTask( python, PRINT_INPUT, inputs );
	}

	/**
	 * We create SharedMemoryImg, which creates and wraps a NDArray internally.
	 * <p>
	 * We use it as Img and put it into the Appose inputs directly.
	 * </p>
	 * <p>
	 * Then we have to preprocess() inputs to find SharedMemoryImgs and replace
	 * them by the wrapped NDArrays.
	 * </p>
	 */
	@Test
	public void version3() throws Exception
	{
		final Img< FloatType > img = new SharedMemoryImg<>( new FloatType(), 4, 3, 2 );

		int i = 0;
		for (FloatType t : img)
			t.set(i++);

		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "img", img);
		doTask( python, PRINT_INPUT, preprocess3(inputs) );
	}

	/**
	 * We create a "normal" ArrayImg (or any other RandomAccessibleInterval),
	 * and it into the Appose inputs directly.
	 * <p>
	 * Then we have to preprocess() inputs to
	 * </p>
	 * <ul>
	 *     <li>find SharedMemoryImgs and replace them by the wrapped NDArrays,
	 *         and</li>
	 *     <li>find any other RandomAccessibleInterval and copy its data into a
	 *         new NDArray.</li>
	 * </ul>
	 * <p>
	 * It is convenient, but maybe it would be better to make explicit copying
	 * necessary?
	 * </p>
	 */
	@Test
	public void version4() throws Exception
	{
		final Img< FloatType > img = ArrayImgs.floats( 4, 3, 2 );

		int i = 0;
		for (FloatType t : img)
			t.set(i++);

		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "img", img);
		doTask( python, PRINT_INPUT, preprocess4(inputs) );
	}

	/**
	 * We create a "normal" ArrayImg (or any other RandomAccessibleInterval),
	 * and put it into the Appose inputs using {@link
	 * NDArrayUtils#asNDArray(RandomAccessibleInterval)}.
	 * <p>
	 * This works with {@link SharedMemoryImg} and other
	 * {@link RandomAccessibleInterval}s, both. It either extracts the wrapped
	 * {@link NDArray}, or copies into a new one.
	 * </p>
	 */
	@Test
	public void version5() throws Exception
	{
		final Img< FloatType > img = ArrayImgs.floats( 4, 3, 2 );

		int i = 0;
		for (FloatType t : img)
			t.set(i++);

		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "img", asNDArray(img) );
		doTask( python, PRINT_INPUT, inputs );
	}

	private List< String > doTask(Service service, String script, Map< String, Object > inputs ) throws IOException, InterruptedException
	{
		Task task = service.task( script, inputs );
		task.waitFor();
		assertSame( Service.TaskStatus.COMPLETE, task.status, task.error );
		String result = ( String ) task.outputs.get( "result" );
		List< String > actual = Arrays.asList( result.split("(\r\n|\n|\r)") );
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
		return actual;
	}

	private static Map< String, Object > preprocess3( final Map< String, Object > inputs )
	{
		inputs.entrySet().forEach( entry -> {
			final Object value = entry.getValue();
			if ( value instanceof SharedMemoryImg )
				entry.setValue( ( ( SharedMemoryImg< ? > ) value ).ndArray() );
		} );
		return inputs;
	}

	private static Map< String, Object > preprocess4( final Map< String, Object > inputs )
	{
		inputs.entrySet().forEach( entry -> {
			final Object value = entry.getValue();
			if ( value instanceof RandomAccessibleInterval )
			{
				final RandomAccessibleInterval rai = ( RandomAccessibleInterval ) value;
				if ( rai.getType() instanceof NativeType )
					entry.setValue( asNDArray( rai, true ) );
			}
		} );
		return inputs;
	}
}
