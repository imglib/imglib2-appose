package net.imglib2.appose.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.apposed.appose.NDArray;

import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.appose.ShmImg;
import net.imglib2.type.NativeType;
import net.imglib2.util.ImgUtil;
import net.imglib2.util.Intervals;

/**
 * Manages named {@link ShmImg} instances.
 * <p>
 * Buffers are reused when dimensions and pixel type match, reallocated when
 * needed, and closed when removed or when this store is closed.
 */
public final class ShmImageStore implements AutoCloseable
{

	private final Map< String, Entry > entries = new LinkedHashMap<>();

	private record Entry( ShmImg< ? > img, Class< ? > typeClass, Dimensions dimensions ) implements AutoCloseable
	{

		private Entry
		{
			Objects.requireNonNull( img, "img" );
			Objects.requireNonNull( typeClass, "typeClass" );
			Objects.requireNonNull( dimensions, "dimensions" );
			dimensions = new FinalDimensions( dimensions );
		}

		boolean matches( final Class< ? > requestedType, final Dimensions requestedDimensions )
		{
			return typeClass.equals( requestedType ) && Intervals.equalDimensions( dimensions, requestedDimensions );
		}

		@Override
		public void close()
		{
			img.close();
		}
	}

	/**
	 * Ensures that a shared-memory image with the given name, type and
	 * dimensions exists and is managed by this store.
	 * <p>
	 * If an existing buffer has matching dimensions and pixel type, it is
	 * reused. Otherwise, the old buffer is closed and a new one is allocated.
	 *
	 * @param name
	 *            buffer name.
	 * @param type
	 *            pixel type.
	 * @param dimensions
	 *            requested image dimensions.
	 */
	public < T extends NativeType< T > > void allocate( final String name, final T type, final Dimensions dimensions )
	{
		ensure( name, type, dimensions );
	}

	private < T extends NativeType< T > > ShmImg< T > ensure( final String name, final T type, final Dimensions dimensions )
	{
		Objects.requireNonNull( name, "name" );
		Objects.requireNonNull( type, "type" );
		Objects.requireNonNull( dimensions, "dimensions" );

		final Class< ? > typeClass = type.getClass();
		final Entry existing = entries.get( name );

		if ( existing != null && existing.matches( typeClass, dimensions ) )
		{
			@SuppressWarnings( "unchecked" )
			final ShmImg< T > img = ( ShmImg< T > ) existing.img();
			return img;
		}

		if ( existing != null )
			existing.close();

		final ShmImg< T > img = new ShmImg<>( type.copy(), toIntArray( dimensions ) );
		entries.put( name, new Entry( img, typeClass, dimensions ) );
		return img;
	}

	/**
	 * Copies an input image into a named buffer of this store.
	 * <p>
	 * The shared-memory image is created or reallocated if its dimensions or
	 * pixel type do not match the input.
	 *
	 * @param name
	 *            buffer name.
	 * @param input
	 *            source image.
	 * @return the shared-memory image containing the copied input.
	 */
	public < T extends NativeType< T > > void write( final String name, final RandomAccessibleInterval< T > input )
	{
		Objects.requireNonNull( input, "input" );

		final T type = input.randomAccess().get().copy();
		final ShmImg< T > shm = ensure( name, type, input );
		ImgUtil.copy( input, shm );
	}

	/**
	 * Copies a named buffer into the specified destination image.
	 *
	 * @param name
	 *            buffer name.
	 * @param destination
	 *            destination image.
	 * @throws IllegalArgumentException
	 *             if the buffer does not exist, or if dimensions or pixel type
	 *             do not match.
	 */
	public < T extends NativeType< T > > void read( final String name, final RandomAccessibleInterval< T > destination )
	{
		Objects.requireNonNull( destination, "destination" );

		final ShmImg< T > source = get( name );

		if ( !Intervals.equalDimensions( ( Dimensions ) source, ( Dimensions ) destination ) )
			throw new IllegalArgumentException(
					"Destination for '" + name + "' has dimensions "
							+ Intervals.toString( destination )
							+ " but buffer has dimensions "
							+ Intervals.toString( source ) );

		final Class< ? > sourceType = source.firstElement().getClass();
		final Class< ? > destinationType = destination.randomAccess().get().getClass();

		if ( !sourceType.equals( destinationType ) )
			throw new IllegalArgumentException(
					"Destination for '" + name + "' has type "
							+ destinationType.getSimpleName()
							+ " but buffer has type "
							+ sourceType.getSimpleName() );

		ImgUtil.copy( source, destination );
	}

	/**
	 * Returns a map containing all named buffers.
	 * <p>
	 * The returned map is suitable for passing to Appose as task inputs.
	 * <p>
	 * You probably should not play with the images in this map. They are
	 * {@link NDArray} which is a shared memory space, that can be closed by
	 * another thread. It is best to use
	 * {@link #copyTo(String, RandomAccessibleInterval)} and
	 * {@link #putInput(String, RandomAccessibleInterval)} to read and write
	 * from the output and input images, respectively.
	 *
	 * @return a new map from buffer names to {@link ShmImg} instances.
	 */
	public Map< String, NDArray > asApposeMap()
	{
		final Map< String, NDArray > map = new LinkedHashMap<>();

		for ( final java.util.Map.Entry< String, Entry > entry : entries.entrySet() )
			map.put( entry.getKey(), entry.getValue().img().ndArray() );

		return map;
	}

	/**
	 * Tests whether a buffer with the given name exists.
	 *
	 * @param name
	 *            buffer name.
	 * @return {@code true} if the buffer exists.
	 */
	public boolean contains( final String name )
	{
		return entries.containsKey( name );
	}

	/**
	 * Removes and closes the named buffer, if it exists.
	 *
	 * @param name
	 *            buffer name.
	 */
	public void remove( final String name )
	{
		final Entry removed = entries.remove( name );
		if ( removed != null )
			removed.close();
	}

	/**
	 * Returns the named shared-memory image.
	 * 
	 * @param name
	 *            buffer name.
	 * @return the shared-memory image.
	 * @throws IllegalArgumentException
	 *             if no buffer with the given name exists.
	 */
	@SuppressWarnings( "unchecked" )
	private < T extends NativeType< T > > ShmImg< T > get( final String name )
	{
		final Entry entry = entries.get( name );
		if ( entry == null )
			throw new IllegalArgumentException( "No shared-memory image named '" + name + "'." );
		return ( ShmImg< T > ) entry.img();
	}

	/**
	 * Closes all managed shared-memory images and clears this store.
	 */
	@Override
	public void close()
	{
		for ( final Entry entry : entries.values() )
			entry.close();
		entries.clear();
	}

	private static int[] toIntArray( final Dimensions dimensions )
	{
		final long[] dims = dimensions.dimensionsAsLongArray();
		final int[] result = new int[ dims.length ];

		for ( int d = 0; d < dims.length; d++ )
			result[ d ] = Math.toIntExact( dims[ d ] );

		return result;
	}
}
