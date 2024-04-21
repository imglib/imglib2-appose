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

import static org.apposed.appose.ndarray.Shape.Order.C_ORDER;

import java.io.IOException;
import java.util.Arrays;

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.ndarray.NDArray;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;

public class PlaygroundNDArray
{
	public static void main( String[] args ) throws IOException, InterruptedException
	{
		final FloatType type = new FloatType();

		final NDArray ndArray = NDArrayUtils.ndArray( type, 4, 3, 2 );
//		final NDArray ndArray = new NDArray( DTypeUtils.dtype( type ), new Shape( F_ORDER, 4, 3, 2 ) );

		final RandomAccessibleInterval< FloatType > img = NDArrayUtils.asArrayImg( ndArray, type );

		int i = 0;
		for ( FloatType t : img )
			t.set( i++ );

//		final UnsignedByteType type = new UnsignedByteType();
//		final NDArray ndArray = new NDArray( type, 4, 3, 2 );
//		final RandomAccessibleInterval< UnsignedByteType > img = NDArrayUtils.asArrayImg( ndArray, type );
//
//		int i = 0;
//		for ( UnsignedByteType t : img )
//			t.set( i++ );

		final Environment env = Appose.base( "/opt/homebrew/Caskroom/miniforge/base/envs/appose/" ).build();
		try ( Service service = env.python() )
		{
			final String script = String.format( PRINT_NDARRAY,
					ndArray.shm().name(),
					ndArray.shape().numElements(),
					ndArray.dType().bytesPerElement(),
					Arrays.toString( ndArray.shape().toIntArray( C_ORDER ) ),
					ndArray.dType().label() );
			System.out.println( script );
//			final Map< String, Object > inputs = new HashMap<>();
//			inputs.put( "img", ndArray);
//			Task task = service.task( script, inputs );
			Task task = service.task( script );
			task.waitFor();
			final String result = ( String ) task.outputs.get( "result" );
			System.out.println( "result = \n" + result );
		}
		ndArray.close();
	}

	// TODO: send NDArray in task.inputs as JSON

	// Format string arguments:
	//  - (String) name of the shared memory segment
	//  - (int) number of elements in the segment
	//  - (int) number of bytes per element
	//  - (String) shape formatted like "[1,2,3]"
	//  - (String) dtype
	private static final String PRINT_NDARRAY = "" + //
			"from multiprocessing import shared_memory\n" + //
			"import numpy as np\n" + //
			"size = %2$d\n" + //
			"bytes_per_element = %3$d\n" + //
			"im_shm = shared_memory.SharedMemory(name='%1$s', size=size * bytes_per_element)\n" + //
			"arr = np.ndarray(size, dtype='%5$s', buffer=im_shm.buf).reshape(%4$s)\n" + //
			"task.outputs['result'] = str(arr)\n" + //
			"im_shm.unlink()";
}
