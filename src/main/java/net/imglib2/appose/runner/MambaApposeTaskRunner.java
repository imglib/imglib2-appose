package net.imglib2.appose.runner;

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

import net.imglib2.appose.util.ApposeTaskListener;

/**
 * It's like the {@link PixiApposeTaskRunner}, but it uses Mamba.
 */
public class MambaApposeTaskRunner implements ApposeTaskRunner
{

	private final URL environmentYamlPath;

	private final URL pythonRunScriptPath;

	private final URL pythonUtilScriptPath;

	private final ApposeTaskListener listener;

	private Service python;

	private String runScript;

	public MambaApposeTaskRunner(
			final URL environmentYamlPath,
			final URL pythonUtilScriptPath,
			final URL pythonRunScriptPath,
			final ApposeTaskListener listener )
	{
		this.environmentYamlPath = environmentYamlPath;
		this.pythonUtilScriptPath = pythonUtilScriptPath;
		this.pythonRunScriptPath = pythonRunScriptPath;
		this.listener = listener;
	}

	/**
	 * Initialize the runner.
	 * <p>
	 * This involves creating the Python environment, running the utility
	 * script, and running the initialization script.
	 *
	 * @throws IOException
	 *             if there is an error reading the initialization or run
	 *             scripts or the environment.yml file.
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
	@Override
	public void init() throws IOException, BuildException, InterruptedException, TaskException
	{
		if ( runScript != null )
			throw new IllegalStateException( "You must call init() only once" );

		// Create Python env.
		final String environmentYamlContent = IOUtils.toString( environmentYamlPath, StandardCharsets.UTF_8 );
		final Environment env = Appose
				.mamba()
				.content( environmentYamlContent )
				.subscribeProgress( listener.progressListener() )
				.subscribeOutput( listener.outputListener() )
				.subscribeError( listener.errorListener() )
				.build();
		final String pythonUtilScript = ( pythonUtilScriptPath == null )
				? ""
				: IOUtils.toString( pythonUtilScriptPath, StandardCharsets.UTF_8 );
		this.python = env.python().init( pythonUtilScript );
		this.runScript = IOUtils.toString( pythonRunScriptPath, StandardCharsets.UTF_8 );
	}

	/**
	 * Execute the Python run script.
	 *
	 * @param inputsParams
	 *            the input parameters to pass to the Python run script.
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
	@Override
	public void run( final Map< String, Object > inputsParams ) throws InterruptedException, TaskException
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
