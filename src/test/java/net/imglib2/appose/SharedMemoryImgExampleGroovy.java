package net.imglib2.appose;

import static net.imglib2.appose.NDArrayUtils.asNDArray;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.Service;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Create an Img, pass it through Appose as an NDArray, wrap it as a
 * SharedMemoryImg on the other end.
 */
public class SharedMemoryImgExampleGroovy
{
	public static void main( String[] args ) throws Exception
	{
		final Img< FloatType > img = ArrayImgs.floats(
				new float[] {
						0, 1, 2,
						3, 4, 5 },
				3, 2 );

		// pass to groovy
		final String home = System.getProperty( "user.home" );
		final List< String > classpath = Arrays.asList(
				home + "/.m2/repository/net/imglib2/imglib2-appose/0.0.1-SNAPSHOT/imglib2-appose-0.0.1-SNAPSHOT.jar",
				home + "/.m2/repository/net/imglib2/imglib2/7.0.2/imglib2-7.0.2.jar" );
		Environment env = Appose.system();
		try ( Service service = env.groovy( classpath ) )
		{
			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "ndarray", asNDArray( img ) );
			Service.Task task = service.task( PRINT_INPUT, inputs );
			task.waitFor();
			System.out.println( "result = " + task.outputs.get( "result" ) );
		}
	}

	private static final String PRINT_INPUT = "import net.imglib2.appose.SharedMemoryImg;\n" + //
			"return new SharedMemoryImg(ndarray).getAt(1,1).get();\n";
}
