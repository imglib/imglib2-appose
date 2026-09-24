/*-
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2024 - 2026 Tobias Pietzsch and Curtis Rueden.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

import org.apposed.appose.NDArray.DType;
import org.junit.jupiter.api.Test;

import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.logic.NativeBoolType;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexDoubleType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Tests {@link DTypes} conversion between Appose and ImgLib2 types.
 */
public class DTypesTest
{
	@Test
	public void testMappings()
	{
		final Set< DType > mapped = EnumSet.noneOf( DType.class );
		mapped.add( assertMapping( new ByteType(), DType.INT8 ) );
		mapped.add( assertMapping( new ShortType(), DType.INT16 ) );
		mapped.add( assertMapping( new IntType(), DType.INT32 ) );
		mapped.add( assertMapping( new LongType(), DType.INT64 ) );
		mapped.add( assertMapping( new UnsignedByteType(), DType.UINT8 ) );
		mapped.add( assertMapping( new UnsignedShortType(), DType.UINT16 ) );
		mapped.add( assertMapping( new UnsignedIntType(), DType.UINT32 ) );
		mapped.add( assertMapping( new UnsignedLongType(), DType.UINT64 ) );
		mapped.add( assertMapping( new FloatType(), DType.FLOAT32 ) );
		mapped.add( assertMapping( new DoubleType(), DType.FLOAT64 ) );
		mapped.add( assertMapping( new ComplexFloatType(), DType.COMPLEX64 ) );
		mapped.add( assertMapping( new ComplexDoubleType(), DType.COMPLEX128 ) );
		mapped.add( assertMapping( new NativeBoolType(), DType.BOOL ) );

		// Every Appose DType should be handled one way or another.
		assertEquals( EnumSet.allOf( DType.class ), mapped );
	}

	/**
	 * Tests that a {@link ShmImg} of each numeric type can be wrapped again
	 * from its {@code NDArray}, with the same type and values.
	 * <p>
	 * Note: {@link NativeBoolType} is excluded, because ImgLib2 has no boolean
	 * {@code BufferAccess} yet, so {@code NDArray}s of {@code bool} cannot be
	 * wrapped.
	 * </p>
	 */
	@Test
	public void testShmImgRoundTrip()
	{
		assertRoundTrip( new ByteType() );
		assertRoundTrip( new ShortType() );
		assertRoundTrip( new IntType() );
		assertRoundTrip( new LongType() );
		assertRoundTrip( new UnsignedByteType() );
		assertRoundTrip( new UnsignedShortType() );
		assertRoundTrip( new UnsignedIntType() );
		assertRoundTrip( new UnsignedLongType() );
		assertRoundTrip( new FloatType() );
		assertRoundTrip( new DoubleType() );
		assertRoundTrip( new ComplexFloatType() );
		assertRoundTrip( new ComplexDoubleType() );
	}

	private static < T extends NativeType< T > > DType assertMapping( final T type, final DType expected )
	{
		final String name = type.getClass().getSimpleName();
		assertSame( expected, DTypes.dtype( type ), name );
		assertSame( type.getClass(), DTypes.type( expected ).get().getClass(), name );

		final PrimitiveType primitiveType = type.getNativeTypeFactory().getPrimitiveType();
		assertSame( primitiveType, DTypes.primitiveType( expected ), name );
		final long bytesPerPixel = type.getEntitiesPerPixel().mulCeil( primitiveType.getByteCount() );
		assertEquals( bytesPerPixel, expected.bytesPerElement(), name );
		return expected;
	}

	private static < T extends NativeType< T > & ComplexType< T > > void assertRoundTrip( final T type )
	{
		final String name = type.getClass().getSimpleName();
		try ( final ShmImg< T > img = new ShmImg<>( type, 4, 3, 2 ) )
		{
			fill( img );
			final ShmImg< T > wrapped = new ShmImg<>( img.ndArray() );
			assertSame( type.getClass(), wrapped.getType().getClass(), name );
			final Iterator< T > expected = img.iterator();
			for ( final T actual : wrapped )
			{
				final T e = expected.next();
				assertEquals( e.getRealDouble(), actual.getRealDouble(), name );
				assertEquals( e.getImaginaryDouble(), actual.getImaginaryDouble(), name );
			}
		}
	}

	/** Fills the image with values 0, 1, 2, ...; for complex types, n - ni. */
	static < T extends NativeType< T > & ComplexType< T > > void fill( final Iterable< T > img )
	{
		int i = 0;
		for ( final T t : img )
		{
			if ( t instanceof RealType )
				( ( RealType< ? > ) t ).setReal( i );
			else
				t.setComplexNumber( i, -i );
			i++;
		}
	}
}
