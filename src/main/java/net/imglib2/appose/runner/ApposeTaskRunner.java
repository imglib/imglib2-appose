package net.imglib2.appose.runner;

import java.io.IOException;
import java.util.Map;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

/**
 * Interface for classes that implements one common way of using Appose. For
 * instance, when using Appose to call a Python script using deep-learning, the
 * life cycle of an instance could be:
 * <ul>
 * <li>Load and initialize a DL model.</li>
 * <li>Run the model on a set of inputs. Do this possibly several times,
 * changing the inputs every-time. This allows for not closing and re-opening
 * the Python env for every image.</li>
 * <li>Close the Python env.</li>
 * </ul>
 * The implementations of this interface are typically abstract classes, one for
 * each Appose environment builder, should aim at reducing the duplicated code
 * in the classes that use Appose, and to provide a common interface across
 * their concrete implementations.
 * <p>
 * Implementations may use different environment managers, such as pixi, uv,
 * conda or another backend. This overly simple interface simply exists so that
 * different implementations can be used in classes that compose a runner with
 * another element.
 */
public interface ApposeTaskRunner extends AutoCloseable
{
	/**
	 * Initializes the execution backend.
	 */
	void init() throws IOException, BuildException, InterruptedException, TaskException;

	/**
	 * Runs a task with the specified Appose parameter map.
	 *
	 * @param inputs
	 *            task input map.
	 */
	void run( Map< String, Object > inputs ) throws InterruptedException, TaskException;

	/**
	 * Closes backend resources.
	 */
	@Override
	void close();
}