package net.imglib2.appose;

import static org.apposed.appose.NDArray.Shape.Order.F_ORDER;

import java.util.Objects;

import org.apposed.appose.DType;
import org.apposed.appose.NDArray;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.nio.BufferAccess;
import net.imglib2.img.basictypeaccess.nio.BufferDataAccessFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.util.Cast;
import net.imglib2.util.Fraction;

public class NDArrayUtils
{
	/**
	 * Allocate an Appose {@link NDArray} with {@link DType} corresponding to
	 * the given ImgLib2 {@code type}.
	 *
	 * @param type
	 * 		ImgLib2 type of {@code NDArray} elements
	 * @param dimensions
	 * 		dimensions of the {@code NDArray} (in F-prder, as used by ImgLib2)
	 * @param <T>
	 * 		element type
	 *
	 * @return a new {@code NDArray}
	 *
	 * @throws IllegalArgumentException
	 * 		If dimensions are too large for the data to fit in an {@code
	 *      ArrayImg}, or if {@code type} has no corresponding {@code DType}.
	 */
	public static < T extends NativeType< T > > NDArray ndArray( final T type, final int... dimensions )
	{
		return new NDArray( DTypeUtils.dtype( type ), new NDArray.Shape( F_ORDER, dimensions ) );
	}

	/**
	 * Wrap an Appose {@link NDArray} as an {@code ArrayImg} with matching type.
	 *
	 * @param ndArray
	 * 		the Appose {@link NDArray} to wrap.
	 * @param <T>
	 * 		pixel type
	 *
	 * @return ArrayImg wrapping {@code ndArray}
	 */
	public static < T extends NativeType< T > > ArrayImg< T, ? > asArrayImg( final NDArray ndArray )
	{
		return asArrayImg( ndArray, Cast.unchecked( DTypeUtils.type( ndArray.dType() ).get() ) );
	}

	/**
	 * Wrap an Appose {@link NDArray} as an {@code ArrayImg<T>}. The specified
	 * {@code T type} must have underlying {@code PrimitiveType} that fits the
	 * {@code ndArray} data type.
	 *
	 * @param ndArray
	 * 		the Appose {@link NDArray} to wrap.
	 * @param type
	 * 		instance of the pixel type
	 * @param <T>
	 * 		pixel type
	 * @param <A>
	 *
	 * @return ArrayImg wrapping {@code ndArray}
	 *
	 * @throws IllegalArgumentException
	 * 		if type doesnt match ndArray type
	 */
	public static < T extends NativeType< T >, A extends BufferAccess< A > > ArrayImg< T, A > asArrayImg(
			final NDArray ndArray,
			final T type )
	{
		if ( !Objects.equals(
				type.getNativeTypeFactory().getPrimitiveType(),
				DTypeUtils.primitiveType( ndArray.dType() ) ) )
			throw new IllegalArgumentException();

		final long[] dimensions = ndArray.shape().toLongArray( F_ORDER );
		final Fraction entitiesPerPixel = type.getEntitiesPerPixel();
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final NativeTypeFactory< T, ? super A > typeFactory = ( NativeTypeFactory ) type.getNativeTypeFactory();
		final A access = BufferDataAccessFactory.get( typeFactory );
		final A data = access.newInstance( ndArray.buffer(), true );
		final ArrayImg< T, A > img = new ArrayImg<>( data, dimensions, entitiesPerPixel );
		img.setLinkedType( typeFactory.createLinkedType( img ) );
		return img;
	}

	/**
	 * Returns a {@code RandomAccessibleInterval} as an Appose {@code NDArray}.
	 * <p>
	 * If the provided {@code rai} is a {@code SharedMemoryImg}, then the
	 * wrapped {@link SharedMemoryImg#ndArray()} is returned.
	 * <p>
	 * Otherwise, if {@code rai} is not a {@code SharedMemoryImg}, it is copied
	 * into a new {@code NDArray} if {@code allowCopy==true}.
	 * <p>
	 * If {@code rai} is not a {@code SharedMemoryImg} and {@code
	 * allowCopy==false}, an {@code IllegalArgumentException} is thrown.
	 *
	 * @param rai
	 * 		image
	 * @param allowCopy
	 * 		specifies what to do if {@code rai} is not a {@code SharedMemoryImg}.
	 * 		If {@code allowCopy==true} then {@code rai} is copied into a new {@code NDArray}.
	 * 		If {@code allowCopy==false} then an {@code IllegalArgumentException} is thrown.
	 * @param <T>
	 * 		pixel type
	 *
	 * @return {@code NDArray} that is wrapped by {@code rai}, or, if {@code rai} does not wrap one then a new {@code NDArray} copy.
	 *
	 * @throws IllegalArgumentException
	 * 		if the provided image is not a {@code SharedMemoryImg} and copying data is not allowed
	 */
	public static < T extends NativeType< T > > NDArray asNDArray( final RandomAccessibleInterval< T > rai, final boolean allowCopy )
	{
		if ( rai instanceof SharedMemoryImg )
			return ( ( SharedMemoryImg< ? > ) rai ).ndArray();
		else if ( allowCopy )
			return SharedMemoryImg.copyOf( rai ).ndArray();
		else
			throw new IllegalArgumentException( "The provided RandomAccessibleInterval is not a SharedMemoryImg" );
	}

	/**
	 * Returns a {@code RandomAccessibleInterval} as an Appose {@code NDArray}.
	 * <p>
	 * If the provided {@code rai} is a {@code SharedMemoryImg}, then the
	 * wrapped {@link SharedMemoryImg#ndArray()} is returned.
	 * <p>
	 * Otherwise, if {@code rai} is not a {@code SharedMemoryImg}, it is copied
	 * into a new {@code NDArray}.
	 *
	 * @param rai
	 * 		image
	 * @param <T>
	 * 		pixel type
	 *
	 * @return {@code NDArray} that is wrapped by {@code rai}, or, if {@code rai} does not wrap one then a new {@code NDArray} copy.
	 *
	 * @throws IllegalArgumentException
	 * 		if the provided image is not a {@code SharedMemoryImg} and copying data is not allowed
	 */
	public static < T extends NativeType< T > > NDArray asNDArray( final RandomAccessibleInterval< T > rai )
	{
		return asNDArray( rai, true );
	}
}
