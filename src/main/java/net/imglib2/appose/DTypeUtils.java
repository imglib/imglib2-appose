package net.imglib2.appose;

import java.util.function.Supplier;

import org.apposed.appose.DType;

import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.logic.NativeBoolType;
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
 * Conversion between Appose and ImgLib2 types
 */
public class DTypeUtils
{
	/**
	 * Get the Appose {@link DType} corresponding to the specified ImgLib2
	 * {@code NativeType}.
	 *
	 * @param type
	 * 		type instance
	 * @param <T>ImgLib2
	 * 		pixel type
	 *
	 * @return the Appose {@code DType} corresponding ot {@code type}.
	 *
	 * @throws IllegalArgumentException
	 * 		if {@code type} has no corresponding {@code DType}
	 */
	public static < T extends NativeType< T > > DType dtype( final T type )
	{
		if ( type instanceof ByteType )
			return DType.INT8;
		else if ( type instanceof ShortType )
			return DType.INT16;
		else if ( type instanceof IntType )
			return DType.INT32;
		else if ( type instanceof LongType )
			return DType.INT64;
		else if ( type instanceof UnsignedByteType )
			return DType.UINT8;
		else if ( type instanceof UnsignedShortType )
			return DType.UINT16;
		else if ( type instanceof UnsignedIntType )
			return DType.UINT32;
		else if ( type instanceof UnsignedLongType )
			return DType.UINT64;
		else if ( type instanceof FloatType )
			return DType.FLOAT32;
		else if ( type instanceof DoubleType )
			return DType.FLOAT64;
		else if ( type instanceof ComplexFloatType )
			return DType.COMPLEX64;
		else if ( type instanceof ComplexDoubleType )
			return DType.COMPLEX64;
		else if ( type instanceof NativeBoolType )
			return DType.BOOL;
		else
			throw new IllegalArgumentException();
	}

	/**
	 * Get the ImgLib2 {@code NativeType} corresponding to the given Appose
	 * {@link DType}.
	 *
	 * @param dType
	 * 		Appose type
	 *
	 * @return a Supplier of ImgLib2 {@code NativeType} corresponding to {@code dType}.
	 */
	public static Supplier< ? extends NativeType< ? > > type( final DType dType )
	{
		switch ( dType )
		{
		case INT8:
			return ByteType::new;
		case INT16:
			return ShortType::new;
		case INT32:
			return IntType::new;
		case INT64:
			return LongType::new;
		case UINT8:
			return UnsignedByteType::new;
		case UINT16:
			return UnsignedShortType::new;
		case UINT32:
			return UnsignedIntType::new;
		case UINT64:
			return UnsignedLongType::new;
		case FLOAT32:
			return FloatType::new;
		case FLOAT64:
			return DoubleType::new;
		case COMPLEX64:
			return ComplexFloatType::new;
		case COMPLEX128:
			return ComplexDoubleType::new;
		case BOOL:
			return NativeBoolType::new;
		default:
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Get the {@link PrimitiveType} underlying the given Appose {@link DType}.
	 *
	 * @param dType
	 * 		Appose type
	 *
	 * @return the primitive type underlying {@code dType}.
	 */
	public static PrimitiveType primitiveType( final DType dType )
	{
		switch ( dType )
		{
		case INT8:
		case UINT8:
			return PrimitiveType.BYTE;
		case INT16:
		case UINT16:
			return PrimitiveType.SHORT;
		case INT32:
		case UINT32:
			return PrimitiveType.INT;
		case INT64:
		case UINT64:
			return PrimitiveType.LONG;
		case FLOAT32:
		case COMPLEX64:
			return PrimitiveType.FLOAT;
		case FLOAT64:
		case COMPLEX128:
			return PrimitiveType.DOUBLE;
		case BOOL:
			return PrimitiveType.BOOLEAN;
		default:
			throw new IllegalArgumentException();
		}
	}
}
