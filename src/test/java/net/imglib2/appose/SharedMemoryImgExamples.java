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

import java.util.HashMap;
import java.util.Map;

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Create an Img, pass it through Appose, and print it in Python as NumPy ndarray.
 * <p>
 * Various possible variants. Not sure all of them are good ideas.
 */
public class SharedMemoryImgExamples
{
	public static void main( String[] args ) throws Exception
	{
//		version1();
//		version2();
		version3();
//		version4();
	}

	private static final String PRINT_INPUT = "" + //
			"import numpy as np\n" + //
			"task.outputs['result'] = str(img.ndarray())";

	/**
	 * We create a NDArray then wrap it into an ArrayImg.
	 * <p>
	 * We have to keep track of the NDArray to pass it to Appose.
	 */
	public static void version1() throws Exception
	{
		final FloatType type = new FloatType();
		final NDArray ndArray = NDArrayUtils.ndArray( type, 4, 3, 2 );
		final Img< FloatType > img = NDArrayUtils.asArrayImg( ndArray, type );

		int i = 0;
		for ( FloatType t : img )
			t.set( i++ );

		final Environment env = Appose.base( "/opt/homebrew/Caskroom/miniforge/base/envs/appose/" ).build();
		try ( Service service = env.python() )
		{
			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "img", ndArray );
			Task task = service.task( PRINT_INPUT, inputs );
			task.waitFor();
			final String result = ( String ) task.outputs.get( "result" );
			System.out.println( "result = \n" + result );
		}
		ndArray.close();
	}

	/**
	 * We create SharedMemoryImg, which creates and wraps a NDArray internally.
	 * <p>
	 * Less cumbersome, but we need to refer to it as SharedMemoryImg to be able
	 * to get the .ndArray() out for passing it to Appose.
	 */
	public static void version2() throws Exception
	{
		final SharedMemoryImg< FloatType > img = new SharedMemoryImg<>( FloatType::new, 4, 3, 2 );

		int i = 0;
		for (FloatType t : img)
			t.set(i++);

		final Environment env = Appose.base( "/opt/homebrew/Caskroom/miniforge/base/envs/appose/" ).build();
		try ( Service service = env.python() )
		{
			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "img", img.ndArray());
			Task task = service.task(PRINT_INPUT, inputs );
			task.waitFor();
			final String result = ( String ) task.outputs.get( "result" );
			System.out.println( "result = \n" + result );
		}
	}



	/**
	 * We create SharedMemoryImg, which creates and wraps a NDArray internally.
	 * <p>
	 * We use it as Img and put it into the Appose inputs directly.
	 * <p>
	 * Then we have to preprocess() inputs to find SharedMemoryImgs and replace
	 * them by the wrapped NDArrays.
	 */
	public static void version3() throws Exception
	{
		final Img< FloatType > img = new SharedMemoryImg<>( FloatType::new, 4, 3, 2 );

		int i = 0;
		for (FloatType t : img)
			t.set(i++);

		final Environment env = Appose.base( "/opt/homebrew/Caskroom/miniforge/base/envs/appose/" ).build();
		try ( Service service = env.python() )
		{
			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "img", img);
			Task task = service.task(PRINT_INPUT, preprocess3(inputs) );
			task.waitFor();
			final String result = ( String ) task.outputs.get( "result" );
			System.out.println( "result = \n" + result );
		}
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






	/**
	 * We create a "normal" ArrayImg (or any other RandomAccessibleInterval),
	 * and it into the Appose inputs directly.
	 * <p>
	 * Then we have to preprocess() inputs to
	 * <ul>
	 *     <li>find SharedMemoryImgs and replace them by the wrapped NDArrays,
	 *         and</li>
	 *     <li>find any other RandomAccessibleInterval and copy its data into a
	 *         new NDArray.</li>
	 * </ul>
	 * It is convenient, but maybe it would be better to make explicit copying
	 * necessary?
	 */
	public static void version4() throws Exception
	{
		final Img< FloatType > img = ArrayImgs.floats( 4, 3, 2 );

		int i = 0;
		for (FloatType t : img)
			t.set(i++);

		final Environment env = Appose.base( "/opt/homebrew/Caskroom/miniforge/base/envs/appose/" ).build();
		try ( Service service = env.python() )
		{
			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "img", img);
			Task task = service.task(PRINT_INPUT, preprocess4(inputs) );
			task.waitFor();
			final String result = ( String ) task.outputs.get( "result" );
			System.out.println( "result = \n" + result );
		}
	}

	private static Map< String, Object > preprocess4( final Map< String, Object > inputs )
	{
		inputs.entrySet().forEach( entry -> {
			final Object value = entry.getValue();
			if ( value instanceof SharedMemoryImg )
			{
				entry.setValue( ( ( SharedMemoryImg< ? > ) value ).ndArray() );
			}
			else if ( value instanceof RandomAccessibleInterval )
			{
				RandomAccessibleInterval rai = ( RandomAccessibleInterval ) value;
				if ( rai.getType() instanceof NativeType ) {
					entry.setValue( SharedMemoryImg.copyOf( rai ).ndArray() );
				}
			}
		} );
		return inputs;
	}
}
