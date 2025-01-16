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
public class ShmImg< T extends NativeType< T > > implements WrappedNDArray, WrappedImg< T >, Img< T > {
	private final NDArray ndArray;

	private final ArrayImg< T, ? > img;

	/**
	 * Copy the given {@code RandomAccessibleInterval} into a new {@link ShmImg}.
	 */
	public static < T extends NativeType< T > > ShmImg< T > copyOf(RandomAccessibleInterval< T > rai )
	{
		final ShmImg< T > copy = new ShmImg<>( rai.getType(), Util.long2int( rai.dimensionsAsLongArray() ) );
		ImgUtil.copy( rai, copy );
		return copy;
	}

	/**
	 * Wrap the specified {@code ndArray} as an {@code ShmImg} with matching type.
	 *
	 * @param ndArray the array to wrap.
	 */
	public ShmImg(final NDArray ndArray )
	{
		this.ndArray = ndArray;
		this.img = NDArrays.asArrayImg( ndArray );
	}

	/**
	 * Create a {@code ShmImg} of the given type and size.
	 *
	 * @param type the type of the image.
	 * @param dimensions the dimensions of the image.
	 * @throws IllegalArgumentException
	 * 		if dimensions are too large for the data to fit in an {@code ArrayImg}
	 */
	public ShmImg(final T type, final int... dimensions  )
	{
		this( type, NDArrays.ndArray( type, dimensions ) );
	}

	private ShmImg(final T type, final NDArray ndArray )
	{
		this.ndArray = ndArray;
		this.img = NDArrays.asArrayImg( ndArray, type );
	}

	// -- WrappedNDArray ------------------------------------------------------

	@Override
	public NDArray ndArray() {
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
		return new ShmImgFactory<>( getType() );
	}

	@Override
	public Img< T > copy()
	{
		return copyOf( this );
	}

	private static class ShmImgFactory< T extends NativeType< T > > extends ImgFactory< T >
	{
		ShmImgFactory( T type )
		{
			super( type );
		}

		@Override
		public Img< T > create( final long... dimensions )
		{
			return new ShmImg<>( type(), Util.long2int( dimensions ) );
		}

		@Override
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public < S > ImgFactory< S > imgFactory( final S type ) throws IncompatibleTypeException
		{
			if ( type instanceof NativeType )
				return new ShmImgFactory( ( NativeType ) type );
			throw new IncompatibleTypeException( this, type.getClass().getCanonicalName() + " does not implement NativeType." );
		}

		@Override
		@Deprecated
		public Img< T > create( final long[] dim, final T type )
		{
			return new ShmImg<>( type, Util.long2int( dim ) );
		}
	}
}
