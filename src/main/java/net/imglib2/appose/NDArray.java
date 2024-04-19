package net.imglib2.appose;

import static net.imglib2.appose.Shape.Order.F_ORDER;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apposed.appose.shm.SharedMemoryArray;

import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;

public class NDArray implements Closeable
{
	private final SharedMemoryArray sharedMemoryArray;

	private final boolean isOwner = true; // TODO: how does this work? Can we make multiple SharedMemoryArray for the same segment, and close each individually???

	private final PrimitiveType primitiveType;

	private final Shape shape;

	public NDArray( final SharedMemoryArray sharedMemoryArray, final PrimitiveType primitiveType, final Shape shape )
	{
		this.sharedMemoryArray = sharedMemoryArray;
		this.primitiveType = primitiveType;
		this.shape = shape;
	}

	public NDArray( final PrimitiveType primitiveType, final Shape shape )
	{
		this( SharedMemoryArray.create(
				safeInt( shape.numElements() * primitiveType.getByteCount() )
		), primitiveType, shape );
	}

	public < T extends NativeType< T > > NDArray( final T type, final int... dimensions )
	{
		this( type.getNativeTypeFactory().getPrimitiveType(), new Shape( F_ORDER, dimensions ) );
	}

	@Override
	public void close() throws IOException
	{
		if ( isOwner )
			sharedMemoryArray.close();
	}

	public PrimitiveType primitiveType()
	{
		return primitiveType;
	}

	public Shape shape()
	{
		return shape;
	}

	public SharedMemoryArray shm()
	{
		return sharedMemoryArray;
	}

	public ByteBuffer buffer()
	{
		final long length = shape.numElements() * primitiveType.getByteCount();
		return sharedMemoryArray.getPointer().getByteBuffer( 0, length );
	}

	private static int safeInt( final long value )
	{
		if ( value > Integer.MAX_VALUE )
			throw new IllegalArgumentException( "value too large" );
		return ( int ) value;
	}
}
