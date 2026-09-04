package net.imglib2.appose.util;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apposed.appose.Appose;
import org.apposed.appose.BuildException;
import org.apposed.appose.Environment;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.apposed.appose.TaskException;

import net.imglib2.appose.ShmImg;

/**
 * A base class to help implementing a common processing pipelines when using
 * Appose to run deep-learning based segmentation algorithms. This class is
 * specifically designed for this purpose; it is likely not what you are looking
 * for if you have a different use case.
 * <p>
 * Gaelle Letort found, when we were working on the Cellpose integration, that
 * you could greatly accelerate processing of many images by initializing the
 * Python environment, loading the DL model in an init script, then reusing the
 * Python env several times on several images. This can be expected as loading a
 * DL model and transferring to the GPU takes a lot of time. This class
 * implements this pattern, but subclasses still must do a lot of work.
 * <p>
 * This runner is using the Pixi engine. It requires:
 * <ul>
 * <li>a path to a pixi.toml file, which describes the Python environment to
 * create and the Python packages to install in it.</li>
 * <li>the environment name to use, as defined in the pixi.toml file</li>
 * <li>a path to a Python utility script that will be run once in the
 * initialization phase. It must not depend on parameters you pass to Appose.
 * This one can simply be used to import Python packages when convenient or
 * define a few functions. For instance in imglib2-cellpose we use it to make
 * sure the numpy imports are done first.</li>
 * <li>a path to a Python initialization script that will be run once as a task,
 * just after the utility script. This one is expected to load the DL model and
 * do any other initialization work that is needed before processing images. It
 * can rely on parameters map passed via Appose (<code>globals()</code>).
 * Typically, these parameters should allow determining what DL model to load
 * and how to configure it. Important: if you want to re-use the model you just
 * loaded later, you must store it the parameters map, for instance by doing
 * <code>task.export( model=model )</code> at the end of the script.</li>
 * </ul>
 * These two scripts are run once, in the {@link #init()} method, which also
 * creates the Python environment.
 * <ul>
 * <p>
 * <li>Then, the {@link #run()} method can be called. It runs a Python task
 * based on the run script, which is expected to process an image. The idea here
 * is not to reload the model, but to re-use the one that was loaded in the init
 * script, and should be now in the parameters map. For instance by doing
 * <code>model = globals()['model']</code> to retrieve the model that was loaded
 * in the init script.</li>
 * </ul>
 * <p>
 * The runner is AutoCloseable, and should be closed when done, to free the
 * Python environment. Subclasses that use {@link ShmImg}s to pass images to the
 * Python script should also close them along with the runner.
 * <p>
 * Because this pattern arose so often when porting segmentation algorithms to
 * imglib2, we made this class to avoid duplicating code. Note however that the
 * inputs and outputs are not handled there, and should be handled by the
 * subclass.
 *
 * @author Jean-Yves Tinevez
 * @see <a href=
 *      "https://github.com/imglib/imglib2-omnipose/blob/main/src/main/java/net/imglib2/omnipose/OmniposeRunner.java">OmniposeRunner</a>
 *      for a brief example.
 *
 */
public class AbstractPixiRunner implements AutoCloseable
{

	private final String envName;

	private final URL pythonRunScriptPath;

	private final URL pythonInitScriptPath;

	private final URL pixiTomlPath;

	private final URL pythonUtilScriptPath;

	private final ApposeTaskListener listener;

	private final Map< String, Object > inputsParams;

	private Service python;

	private String runScript;

	protected AbstractPixiRunner(
			final Map< String, Object > inputsParams,
			final URL pixiTomlPath,
			final URL pythonUtilScriptPath,
			final URL pythonInitScriptPath,
			final URL pythonRunScriptPath,
			final String envName,
			final ApposeTaskListener listener )
	{
		this.pixiTomlPath = pixiTomlPath;
		this.pythonUtilScriptPath = pythonUtilScriptPath;
		this.pythonInitScriptPath = pythonInitScriptPath;
		this.pythonRunScriptPath = pythonRunScriptPath;
		this.envName = envName;
		this.listener = listener;
		this.inputsParams = inputsParams;
	}

	/**
	 * Initialize the runner.
	 * <p>
	 * This involves creating the Python environment, running the utility
	 * script, and running the initialization script.
	 *
	 * @throws IOException
	 *             if there is an error reading the initialization or run
	 *             scripts or the pixi.toml file.
	 * @throws BuildException
	 *             if there is an error building the Python environment.
	 * @throws InterruptedException
	 *             if the thread is interrupted while waiting for the
	 *             initialization task to complete.
	 * @throws TaskException
	 *             if there is an error running the Python initialization task.
	 * @throws RuntimeException
	 *             if the Python initialization task fails with an error.
	 */
	public void init() throws IOException, BuildException, InterruptedException, TaskException
	{
		// Create Python env.
		final String pixiTomlContent = IOUtils.toString( pixiTomlPath, StandardCharsets.UTF_8 );
		final Environment env = Appose
				.pixi()
				.content( pixiTomlContent )
				.subscribeProgress( listener.progressListener() )
				.subscribeOutput( listener.outputListener() )
				.subscribeError( listener.errorListener() )
				.build();
		final String pythonUtilScript = IOUtils.toString( pythonUtilScriptPath, StandardCharsets.UTF_8 );
		this.python = env.activate( envName ).python().init( pythonUtilScript );

		// The Python initialization task.
		final String pythonInitScript = IOUtils.toString( pythonInitScriptPath, StandardCharsets.UTF_8 );
		final Task task = python.task( pythonInitScript, inputsParams );

		final long start = System.currentTimeMillis();
		// To catch update message from the python script
		task.listen( listener.taskListener() );
		task.start();
		// Wait for task completion.
		task.waitFor();

		// Verify that it worked.
		if ( task.status != TaskStatus.COMPLETE )
			throw new RuntimeException( "Python script failed with error: " + task.error );

		// Benchmark.
		final long end = System.currentTimeMillis();
		listener.message( "Initialization done in " + ( end - start ) / 1000. + " s" );

		// Load it now and only once.
		this.runScript = IOUtils.toString( pythonRunScriptPath, StandardCharsets.UTF_8 );
	}

	/**
	 * Execute the Python run script.
	 *
	 * @throws InterruptedException
	 *             if the thread is interrupted while waiting for the run task
	 *             to complete.
	 * @throws TaskException
	 *             if there is an error running the Python run task.
	 * @throws IllegalStateException
	 *             if the runner has not been initialized by calling
	 *             {@link #init()} before calling this method.
	 * @throws RuntimeException
	 *             if the Python run task fails with an error.
	 */
	public void run() throws InterruptedException, TaskException
	{
		if ( runScript == null )
			throw new IllegalStateException( "You must call init() before calling run()" );

		// The Python task.
		final Task task = python.task( runScript, inputsParams );

		final long start = System.currentTimeMillis();
		// To catch update message from the python script
		task.listen( listener.taskListener() );
		task.start();
		// Wait for task completion.
		task.waitFor();

		// Verify that it worked.
		if ( task.status != TaskStatus.COMPLETE )
			throw new RuntimeException( "Python script failed with error: " + task.error );

		// Benchmark.
		final long end = System.currentTimeMillis();
		listener.message( "Python done in " + ( end - start ) / 1000. + " s" );
	}

	@Override
	public void close()
	{
		if ( python != null )
			python.close();
	}
}
