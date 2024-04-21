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

import static net.imglib2.appose.Shape.Order.C_ORDER;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import groovy.json.JsonOutput;
import net.imglib2.type.numeric.real.FloatType;

public class PlaygroundNDArrayJson
{
	public static class NDArrayJson
	{
		public static String toJson( final NDArray ndArray )
		{
			return JsonOutput.toJson( toMap( ndArray ) );
		}

		public static Map< String, Object > toMap( final NDArray ndArray )
		{
			final Map< String, Object > map = new LinkedHashMap<>();

			map.put( "appose_ndarray", "v0.0.1" );
			map.put( "shm_name", ndArray.shm().name() );
			map.put( "shm_size", ndArray.shm().size() );
			map.put( "dtype", ndArray.dType().json() );
			map.put( "shape", ndArray.shape().asIntArray( C_ORDER ) );

			return map;
		}
	}


	static class NDArrayInfo
	{
		private final String appose_ndarray = "v0.0.1";
		private final String shm_name;
		private final long shm_size;
		private final String dtype;
		private final int[] shape;

		public NDArrayInfo( final NDArray ndArray  )
		{
			this( ndArray.shm().name(), ndArray.shm().size(), ndArray.dType().json(), ndArray.shape().asIntArray( C_ORDER ) );
		}

		public NDArrayInfo( final String shm_name, final long shm_size, final String dtype, final int[] shape )
		{
			this.shm_name = shm_name;
			this.shm_size = shm_size;
			this.dtype = dtype;
			this.shape = shape;
		}
	}

	public static void main( String[] args ) throws IOException, InterruptedException
	{
		final FloatType type = new FloatType();
		final NDArray ndArray = new NDArray( type, 4, 3, 2 );

		System.out.println( "the_json = '" + NDArrayJson.toJson( ndArray ) + "'" );

//		final NDArrayInfo info = new NDArrayInfo( ndArray );
//		String encode = JsonOutput.toJson(info.intArray);
//		System.out.println( "encode = `" + encode + "`" );
//
		final Gson gson = new GsonBuilder().create();
		String encode = gson.toJson( new NDArrayInfo( ndArray ) );
		System.out.println( "gson = '" + encode + "'" );

		while ( true )
		{
			Thread.sleep( 1000 );
			System.out.print( "." );
		}
//		ndArray.close();
	}

	// TODO: send NDArray in task.inputs as JSON

	// Format string arguments:
	//  - (String) name of the shared memory segment
	//  - (int) number of elements in the segment
	//  - (int) number of bytes per element
	//  - (String) shape formatted like "[1,2,3]"
	//  - TODO: send dtype too
	private static final String PRINT_NDARRAY = "" + //
			"from multiprocessing import shared_memory\n" + //
			"import numpy as np\n" + //
			"size = %2$d\n" + //
			"bytes_per_element = %3$d\n" + //
			"im_shm = shared_memory.SharedMemory(name='%1$s', size=size * bytes_per_element)\n" + //
			"arr = np.ndarray(size, dtype='float32', buffer=im_shm.buf).reshape(%4$s)\n" + //
			"task.outputs['result'] = str(arr)\n" + //
			"im_shm.unlink()";
}
