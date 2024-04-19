package net.imglib2.appose;

import java.util.Arrays;

import net.imglib2.Dimensions;
import net.imglib2.util.Intervals;

public class Shape
{
	public enum Order
	{
		C_ORDER,
		F_ORDER;

		Order reverse()
		{
			return this == C_ORDER ? F_ORDER : C_ORDER;
		}
	}

	public Shape( final Order order, final int... shape )
	{
		this.order = order;
		this.shape = shape;
		this.reverseShape = new Shape( order.reverse(), reverse( shape ), this );
	}

	private Shape( final Order order, final int[] shape, final Shape reverseShape )
	{
		this.order = order;
		this.shape = shape;
		this.reverseShape = reverseShape;
	}

	public int get( final int d )
	{
		return shape[ d ];
	}

	public int length()
	{
		return shape.length;
	}

	public Order order()
	{
		return order;
	}

	public Dimensions dimensions()
	{
		return dimensions;
	}

	public long numElements()
	{
		return Intervals.numElements( shape );
	}

	public int[] asIntArray( final Order order )
	{
		return with( order ).shape;
	}

	public Shape with( final Order order ) // rename to as(Order) ?
	{
		return order.equals( this.order ) ? this : reverseShape;
	}

	private final Order order;

	private final int[] shape;

	private final Shape reverseShape;

	private final Dimensions dimensions = new Dimensions()
	{
		@Override
		public long dimension( final int d )
		{
			return get( d );
		}

		@Override
		public int numDimensions()
		{
			return length();
		}
	};

	private static int[] reverse( int[] array )
	{
		final int[] reverse = new int[ array.length ];
		Arrays.setAll( reverse, i -> array[ array.length - i - 1 ] );
		return reverse;
	}
}
