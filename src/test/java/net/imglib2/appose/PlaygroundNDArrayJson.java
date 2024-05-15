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

import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;

import java.util.HashMap;
import java.util.Map;

public class PlaygroundNDArrayJson
{
	public static void main( String[] args ) throws Exception
	{
		final FloatType type = new FloatType();
		final NDArray ndArray = NDArrayUtils.ndArray( type, 4, 3, 2 );
		final Img<FloatType> img = NDArrayUtils.asArrayImg(ndArray, type);

		int i = 0;
		for (FloatType t : img)
			t.set(i++);

		final Environment env = Appose.base( "/opt/homebrew/Caskroom/miniforge/base/envs/appose/" ).build();
		try ( Service service = env.python() )
		{
			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "img", ndArray);
			Task task = service.task(PRINT_INPUT, inputs );
			task.waitFor();
			final String result = ( String ) task.outputs.get( "result" );
			System.out.println( "result = \n" + result );
		}
		ndArray.close();
	}

	private static final String PRINT_INPUT = "" + //
		"import numpy as np\n" + //
		"task.outputs['result'] = str(img.ndarray())";

}
