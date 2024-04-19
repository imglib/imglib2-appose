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

import java.io.IOException;

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.shm.SharedMemoryArray;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.type.numeric.real.FloatType;

public class PlaygroundNDArray
{
	public static void main( String[] args ) throws IOException, InterruptedException
	{
		final FloatType type = new FloatType();
//		final NDArray ndArray = new NDArray(
//				type.getNativeTypeFactory().getPrimitiveType(),
//				new Shape( Shape.Order.F_ORDER, 4, 3, 2 ) );

		final NDArray ndArray = new NDArray( type, 4, 3, 2 );
		final RandomAccessibleInterval< FloatType > img = NDArrayUtils.asArrayImg( ndArray, type );

		int i = 0;
		for ( FloatType t : img )
			t.set( i++ );

		final Environment env = Appose.base( "/opt/homebrew/Caskroom/miniforge/base/envs/appose/" ).build();
		try ( Service service = env.python() )
		{
			final String script = String.format( PRINT_NDARRAY, ndArray.shm().getNameForPython() );
//			System.out.println( script );
			Task task = service.task( script );
			task.waitFor();
//			System.out.println("task.inputs = " + task.inputs);
//			System.out.println("task.outputs = " + task.outputs);
			final String result = ( String ) task.outputs.get( "result" );
			System.out.println( "result = \n" + result );
		}
		ndArray.close();
	}

	private static final String PRINT_NDARRAY = "" + //
			"from multiprocessing import shared_memory\n" + //
			"import numpy as np\n" + //
			"size = 24\n" + //
			"im_shm = shared_memory.SharedMemory(name='%s', size=size * 4)\n" + //
			"arr = np.ndarray(size, dtype='float32', buffer=im_shm.buf).reshape([2, 3, 4])\n" + //
			"task.outputs['result'] = str(arr)\n" + //
			"im_shm.unlink()";
}
