package net.imglib2.appose;

import static org.apposed.appose.NDArray.Shape.Order.F_ORDER;

import java.util.Objects;

import org.apposed.appose.NDArray;

import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.nio.BufferAccess;
import net.imglib2.img.basictypeaccess.nio.BufferDataAccessFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.util.Fraction;

public class NDArrayUtils
{
	public static < T extends NativeType< T > > NDArray ndArray( final T type, final int... dimensions )
	{
		return new NDArray( DTypeUtils.dtype( type ), new NDArray.Shape( F_ORDER, dimensions ) );
	}

	static < T extends NativeType< T >, A extends BufferAccess< A > > ArrayImg< T, A > asArrayImg(
			final NDArray ndArray,
			final T type )
	{
		if ( !Objects.equals(
				type.getNativeTypeFactory().getPrimitiveType(),
				DTypeUtils.primitiveType( ndArray.dType() ) ) )
			throw new IllegalArgumentException();

//		final long[] dimensions = ndArray.shape().to( F_ORDER ).toLongArray();
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
}
