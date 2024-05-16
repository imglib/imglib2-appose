package net.imglib2.appose;

import org.apposed.appose.NDArray;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.WrappedImg;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.stream.LocalizableSpliterator;
import net.imglib2.type.NativeType;
import net.imglib2.util.ImgUtil;
import net.imglib2.util.Util;

/**
 * Wraps an {@code ArrayImg} backed by Appose {@link NDArray}.
 * <p>
 * {@code RandomAccessibleInterval} methods are forwarded to the {@code ArrayImg}.
 * <p>
 * Use {@link #ndArray} to get the wrapped Appose {@link NDArray}.
 *
 * @param <T>
 */
public class SharedMemoryImg< T extends NativeType< T > > implements RandomAccessibleInterval< T >, WrappedImg< T >, Img< T >
{
	private final NDArray ndArray;

	private final ArrayImg< T, ? > img;

	/**
	 * Copy the given {@code RandomAccessibleInterval} into a new {@link SharedMemoryImg}.
	 */
	public static < T extends NativeType< T > > SharedMemoryImg< T > copyOf( RandomAccessibleInterval< T > rai )
	{
		final SharedMemoryImg< T > copy = new SharedMemoryImg<>( rai.getType(), Util.long2int( rai.dimensionsAsLongArray() ) );
		ImgUtil.copy( rai, copy );
		return copy;
	}

	/**
	 * Wrap the specified {@code ndArray} as an {@code SharedMemoryImg} with matching type.
	 *
	 * @param ndArray
	 */
	public SharedMemoryImg( final NDArray ndArray )
	{
		this.ndArray = ndArray;
		this.img = NDArrayUtils.asArrayImg( ndArray );
	}

	/**
	 * Create a {@code SharedMemoryImg} of the given type and size.
	 *
	 * @param type
	 * @param dimensions
	 * @throws IllegalArgumentException
	 * 		if dimensions are too large for the data to fit in an {@code ArrayImg}
	 */
	public SharedMemoryImg( final T type, final int... dimensions  )
	{
		this( type, NDArrayUtils.ndArray( type, dimensions ) );
	}

	private SharedMemoryImg( final T type, final NDArray ndArray )
	{
		this.ndArray = ndArray;
		this.img = NDArrayUtils.asArrayImg( ndArray, type );
	}

	/**
	 * Get the {@code NDArray} storing the image data.
	 *
	 * @return the wrapped {@code NDArray}.
	 */
	public NDArray ndArray()
	{
		return ndArray;
	}

	// -- WrappedImg ----------------------------------------------------------

	@Override
	public ArrayImg< T, ? > getImg()
	{
		return img;
	}

	// -- RandomAccessibleInterval --------------------------------------------

	@Override
	public T getType()
	{
		return img.getType();
	}

	@Override
	public int numDimensions()
	{
		return img.numDimensions();
	}

	@Override
	public long min( final int d )
	{
		return img.min( d );
	}

	@Override
	public long max( final int d )
	{
		return img.max( d );
	}

	@Override
	public long dimension( final int d )
	{
		return img.dimension( d );
	}

	@Override
	public RandomAccess< T > randomAccess()
	{
		return img.randomAccess();
	}

	@Override
	public RandomAccess< T > randomAccess( final Interval interval )
	{
		return img.randomAccess( interval );
	}

	@Override
	public Cursor< T > cursor()
	{
		return img.cursor();
	}

	@Override
	public Cursor< T > localizingCursor()
	{
		return img.localizingCursor();
	}

	@Override
	public LocalizableSpliterator< T > spliterator()
	{
		return img.spliterator();
	}

	@Override
	public LocalizableSpliterator< T > localizingSpliterator()
	{
		return img.localizingSpliterator();
	}

	@Override
	public long size()
	{
		return img.size();
	}

	@Override
	public Object iterationOrder()
	{
		return img.iterationOrder();
	}


	// -- Img -----------------------------------------------------------------

	@Override
	public ImgFactory< T > factory()
	{
		return new SharedMemoryImgFactory<>( getType() );
	}

	@Override
	public Img< T > copy()
	{
		return copyOf( this );
	}

	private static class SharedMemoryImgFactory< T extends NativeType< T > > extends ImgFactory< T >
	{
		SharedMemoryImgFactory( T type )
		{
			super( type );
		}

		@Override
		public Img< T > create( final long... dimensions )
		{
			return new SharedMemoryImg<>( type(), Util.long2int( dimensions ) );
		}

		@Override
		public < S > ImgFactory< S > imgFactory( final S type ) throws IncompatibleTypeException
		{
			if ( type instanceof NativeType )
				return new SharedMemoryImgFactory( ( NativeType ) type );
			throw new IncompatibleTypeException( this, type.getClass().getCanonicalName() + " does not implement NativeType." );
		}

		@Override
		@Deprecated
		public Img< T > create( final long[] dim, final T type )
		{
			return new SharedMemoryImg<>( type, Util.long2int( dim ) );
		}
	}
}
