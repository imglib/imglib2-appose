package net.imglib2.appose;

import org.apposed.appose.NDArray;

/**
 * An object that wraps an {@link NDArray} somehow.
 */
public interface WrappedNDArray extends AutoCloseable
{
	NDArray ndArray();

	@Override
	default void close()
	{
		ndArray().close();
	}
}
