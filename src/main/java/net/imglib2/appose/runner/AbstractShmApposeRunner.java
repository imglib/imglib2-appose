package net.imglib2.appose.runner;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.apposed.appose.BuildException;
import org.apposed.appose.NDArray;
import org.apposed.appose.TaskException;

import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;

/**
 * Base class for Appose runners using named shared-memory image buffers.
 * <p>
 * This class uses composition: task execution is delegated to an
 * {@link ApposeTaskRunner}, while image buffers are managed by a
 * {@link ShmImageStore}.
 */
public abstract class AbstractShmApposeRunner implements AutoCloseable
{

	private final ApposeTaskRunner runner;

	private final ShmImageStore store;

	private boolean processed = false;

	protected AbstractShmApposeRunner( final ApposeTaskRunner runner, final ShmImageStore images )
	{
		this.runner = Objects.requireNonNull( runner, "runner" );
		this.store = Objects.requireNonNull( images, "images" );
	}

	protected AbstractShmApposeRunner( final ApposeTaskRunner runner )
	{
		this( runner, new ShmImageStore() );
	}

	public final void init() throws IOException, BuildException, InterruptedException, TaskException
	{
		runner.init();
	}

	protected final < T extends NativeType< T > > void allocateImage( final String name, final T type, final Dimensions dimensions )
	{
		store.allocate( name, type, dimensions );
		processed = false;
	}

	protected final < T extends NativeType< T > > void writeImage( final String name, final RandomAccessibleInterval< T > input )
	{
		store.write( name, input );
		processed = false;
	}

	protected final < T extends NativeType< T > > void readImage( final String name, final RandomAccessibleInterval< T > output )
	{
		requireProcessed();
		store.read( name, output );
	}

	protected final boolean hasImage( final String name )
	{
		return store.contains( name );
	}

	protected final void removeImage( final String name )
	{
		store.remove( name );
		processed = false;
	}

	protected final void markDirty()
	{
		processed = false;
	}

	protected final void requireProcessed()
	{
		if ( !processed )
			throw new IllegalStateException( "The task has not been run yet. Please execute run() first." );
	}

	protected final Map< String, NDArray > apposeImages()
	{
		return store.asApposeMap();
	}

	/**
	 * Combines scalar/task parameters with all managed image buffers.
	 * <p>
	 * Throws if a scalar key collides with an image-buffer key.
	 */
	protected final Map< String, Object > apposeMap( final Map< String, Object > parameters )
	{
		Objects.requireNonNull( parameters, "parameters" );
		final Map< String, Object > map = new LinkedHashMap<>( parameters );
		for ( final Entry< String, NDArray > image : store.asApposeMap().entrySet() )
		{
			if ( map.containsKey( image.getKey() ) )
				throw new IllegalArgumentException( "Duplicate Appose task key: '" + image.getKey() + "'." );

			map.put( image.getKey(), image.getValue() );
		}
		return map;
	}

	protected final void runTask( final Map< String, Object > map ) throws InterruptedException, TaskException
	{
		runner.run( map );
		processed = true;
	}

	@Override
	public void close()
	{
		try
		{
			runner.close();
		}
		finally
		{
			store.close();
		}
	}
}
