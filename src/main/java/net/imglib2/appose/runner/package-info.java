/**
 * This package contains a utility hierarchy that aims at limiting duplicating
 * code in artifacts that use imglib2-appose to run non-Java algorithms that
 * operate on images. Typically Deep Learning-based segmentation algorithms
 * implemented in Python.
 * 
 * The utility takes the shape of runners that wrap an
 * {@link org.apposed.appose.Service} and 'enforce' a certain life cycle: build
 * an execution environment once, run tasks on it many times, then release it.
 *
 * <h2>Why this package exists</h2>
 * 
 * When using Appose to call Python code — typically deep-learning based
 * segmentation — the same pattern arises again and again:
 * 
 * <ol>
 * <li>Build and initialize an execution environment (e.g. a Python environment,
 * or more precisely a Python service) that may be expensive to create.</li>
 * 
 * <li>Run a task on it, possibly many times, possibly changing the inputs each
 * time.</li>
 * 
 * <li>Release the environment when done.</li>
 * </ol>
 * 
 * In particular, for deep-learning models, it is crucial <em>not</em> to
 * recreate the environment for every image: creating the env, loading a model
 * and transferring it to the GPU dominates the processing time. It must be done
 * once, and the model kept alive (typically in the script's {@code globals()})
 * so that subsequent tasks can reuse it.
 * 
 * <p>
 * 
 * This package factors that pattern out so that concrete runners (e.g. one per
 * script Cellpose, Omnipose, etc.) — only declare <em>what</em> to run, not
 * <em>how</em> to run it, and so that different backends (pixi, conda, uv, …)
 * can be swapped behind a common interface.
 *
 * <h2>How to use it</h2>
 * 
 * The package is built around two orthogonal pieces composed together:
 * 
 * <ul>
 * <li>An {@link ApposeTaskRunner} — the execution backend. It knows how to
 * build an environment ({@link ApposeTaskRunner#init()}), execute a script with
 * a parameter map ({@link ApposeTaskRunner#run(Map)}), and release the
 * environment ({@link ApposeTaskRunner#close()}). {@link PixiApposeTaskRunner}
 * is the implementation based on the pixi engine: it takes a {@code pixi.toml}
 * descriptor, an environment name, an optional utility script (executed once
 * when the Python service is created, before any task — useful for imports),
 * and the run script executed at each {@code run()} call.</li>
 * 
 * <li>A {@link net.imglib2.appose.ShmImg}-based image store — shared-memory
 * image buffers exchanged with the Python side without serialization.
 * {@link ShmImageStore} manages them by name: buffers are reused when
 * dimensions and pixel type match, reallocated otherwise, and closed when
 * removed or when the store is closed.</li>
 * </ul>
 * 
 * {@link AbstractShmApposeRunner} combines both by composition: it delegates
 * task execution to an {@link ApposeTaskRunner}, manages buffers through a
 * {@link ShmImageStore}, and merges scalar parameters and image buffers into
 * the single map passed to Appose. Concrete subclasses add the script-specific
 * glue: declaring the named buffers, mapping domain parameters, and exposing
 * typed getters for the outputs.
 *
 * <h2>Example</h2>
 * 
 * A typical implementation mirrors
 * {@link net.imglib2.appose.runner.AbstractShmApposeRunner} subclasses such as
 * the {@code OmniposeRunner} of the imglib2-omnipose project:
 *
 * <pre>
 * 
 * public class MyRunner extends AbstractShmApposeRunner
 * {
 *
 * 	private static final String INPUT = "input";
 *
 * 	private static final String OUTPUT = "output";
 *
 * 	private MyRunner( final String envName, final ApposeTaskListener listener )
 * 	{
 * 		super( new PixiApposeTaskRunner(
 * 				MyRunner.class.getResource( "/pixi.toml" ),
 * 				MyRunner.class.getResource( "/my_utils.py" ),
 * 				MyRunner.class.getResource( "/my_run.py" ),
 * 				envName,
 * 				listener ) );
 * 	}
 *
 * 	public < T extends RealType< T > & NativeType< T > > void setInput( final RandomAccessibleInterval< T > input )
 * 	{
 * 		writeImage( INPUT, input );
 * 		allocateImage( OUTPUT, new UnsignedShortType(), input );
 * 	}
 *
 * 	public void run( final Map< String, Object > parameters ) throws InterruptedException, TaskException
 * 	{
 * 		if ( !hasImage( INPUT ) )
 * 			throw new IllegalStateException( "Please call setInput() first." );
 * 		runTask( apposeMap( parameters ) );
 * 	}
 *
 * 	public < R extends IntegerType< R > & NativeType< R > > void getOutput( final RandomAccessibleInterval< R > output )
 * 	{
 * 		readImage( OUTPUT, output );
 * 	}
 * }
 * 
 * </pre>
 * 
 * and its use from client code:
 *
 * <pre>
 * try (MyRunner runner = new MyRunner( "my-env", ApposeTaskListener.STD ))
 * {
 * 	runner.init(); // builds the environment, runs the utility script.
 * 
 * 	runner.setInput( image );
 * 	runner.run( myParameters );
 * 	runner.getOutput( output );
 * 
 * 	runner.setInput( anotherImage ); // buffers are reused: no reallocation.
 * 	runner.run( myParameters );
 * 	runner.getOutput( anotherOutput );
 * 
 * } // close() releases the environment and the shared-memory buffers.
 * </pre>
 *
 * @author Jean-Yves Tinevez
 */
package net.imglib2.appose.runner;
