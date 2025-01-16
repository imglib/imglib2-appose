/*-
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2024 - 2025 Tobias Pietzsch and Curtis Rueden.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package net.imglib2.appose;

import static org.apposed.appose.NDArray.Shape.Order.F_ORDER;

import java.util.Objects;

import org.apposed.appose.NDArray;
import org.apposed.appose.NDArray.DType;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.nio.BufferAccess;
import net.imglib2.img.basictypeaccess.nio.BufferDataAccessFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.util.Cast;
import net.imglib2.util.Fraction;

public class NDArrays
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
		return new NDArray( DTypes.dtype( type ), new NDArray.Shape( F_ORDER, dimensions ) );
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
		return asArrayImg( ndArray, Cast.unchecked( DTypes.type( ndArray.dType() ).get() ) );
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
				DTypes.primitiveType( ndArray.dType() ) ) )
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
	 * If the provided {@code rai} wraps an {@code NDArray}, then the
	 * {@link WrappedNDArray#ndArray()} is returned.
	 * <p>
	 * Otherwise, if {@code rai} does not wrap an {@code NDArray}, it is copied
	 * into a new {@code NDArray} if {@code allowCopy==true}.
	 * <p>
	 * If {@code rai} does not wrap an {@code NDArray} and
	 * {@code allowCopy==false}, an {@code IllegalArgumentException} is thrown.
	 *
	 * @param rai
	 * 		image
	 * @param allowCopy
	 * 		specifies what to do if {@code rai} does not wrap an {@code NDArray}.
	 * 		If {@code allowCopy==true} then {@code rai} is copied into a new {@code NDArray}.
	 * 		If {@code allowCopy==false} then an {@code IllegalArgumentException} is thrown.
	 * @param <T>
	 * 		pixel type
	 *
	 * @return {@code NDArray} that is wrapped by {@code rai}, or, if {@code rai} does not wrap one then a new {@code NDArray} copy.
	 *
	 * @throws IllegalArgumentException
	 * 		if the provided image does not wrap an {@code NDArray} and copying data is not allowed
	 */
	public static < T extends NativeType< T > > NDArray asNDArray( final RandomAccessibleInterval< T > rai, final boolean allowCopy )
	{
		if ( rai instanceof WrappedNDArray )
			return ( ( WrappedNDArray ) rai ).ndArray();
		else if ( allowCopy )
			return ShmImg.copyOf( rai ).ndArray();
		else
			throw new IllegalArgumentException( "The provided RandomAccessibleInterval does not wrap an NDArray" );
	}

	/**
	 * Returns a {@code RandomAccessibleInterval} as an Appose {@code NDArray}.
	 * <p>
	 * If the provided {@code rai} wraps an {@code NDArray}, then the
	 * {@link WrappedNDArray#ndArray()} is returned.
	 * </p>
	 * <p>
	 * Otherwise, if {@code rai} does not wrap an {@code NDArray}, it is copied
	 * into a new {@code NDArray}.
	 * </p>
	 *
	 * @param rai
	 * 		image
	 * @param <T>
	 * 		pixel type
	 *
	 * @return {@code NDArray} that is wrapped by {@code rai}, or, if {@code rai} does not wrap one then a new {@code NDArray} copy.
	 *
	 * @throws IllegalArgumentException
	 * 		if the provided image does not wrap an {@code NDArray} and copying data is not allowed
	 */
	public static < T extends NativeType< T > > NDArray asNDArray( final RandomAccessibleInterval< T > rai )
	{
		return asNDArray( rai, true );
	}
}
