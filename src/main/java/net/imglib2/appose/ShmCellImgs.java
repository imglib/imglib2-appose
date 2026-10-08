package net.imglib2.appose;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.imglib2.RandomAccessible;
import net.imglib2.blocks.SubArrayCopy;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.CacheRemover;
import net.imglib2.cache.LoaderRemoverCache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.ref.GuardedStrongRefLoaderRemoverCache;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.basictypeaccess.nio.BufferAccess;
import net.imglib2.img.basictypeaccess.nio.BufferDataAccessFactory;
import net.imglib2.img.basictypeaccess.nio.ByteBufferAccess;
import net.imglib2.img.basictypeaccess.nio.CharBufferAccess;
import net.imglib2.img.basictypeaccess.nio.DoubleBufferAccess;
import net.imglib2.img.basictypeaccess.nio.FloatBufferAccess;
import net.imglib2.img.basictypeaccess.nio.IntBufferAccess;
import net.imglib2.img.basictypeaccess.nio.LongBufferAccess;
import net.imglib2.img.basictypeaccess.nio.ShortBufferAccess;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.CellGrid.CellDimensionsAndSteps;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.type.PrimitiveType;
import net.imglib2.util.Cast;
import net.imglib2.util.Fraction;
import net.imglib2.util.Util;
import org.apposed.appose.NDArray;

import static org.apposed.appose.NDArray.Shape.Order.F_ORDER;

public class ShmCellImgs {

	public interface NDArrayFactory {

		NDArray createNDArray(final NDArray.DType dType, final NDArray.Shape shape);
	}

	/**
	 * Wrap a source {@code CellImg} into a NDArray backed {@code CachedCellImg}.
	 */
	public static <T extends NativeType<T>, A extends ArrayDataAccess<A> & BufferAccess<A>> CachedCellImg<T, A> createShmCellImg(
			final AbstractCellImg<T, ?, ?, ?> source,
			final NDArrayFactory ndArrayFactory) {

		final T type = source.getType();
		final CellGrid grid = source.getCellGrid();

		// Create a CacheLoader that copies cells into NDArray buffers
		final CacheLoader<Long, Cell<A>> loader = new ShmCellLoader<>(source, ndArrayFactory);

		// Create a CacheRemover that closes NDArray of discarded ShmCells.
		final CacheRemover<Long, Cell<A>, NDArray> remover = new ShmCellRemover<>(type, grid);

		final LoaderRemoverCache<Long, Cell<A>, NDArray> loaderRemoverCache = new GuardedStrongRefLoaderRemoverCache<>(0);
		final Cache<Long, Cell<A>> cache = loaderRemoverCache.withRemover(remover).withLoader(loader);

		final NativeTypeFactory<T, A> typeFactory = Cast.unchecked(type.getNativeTypeFactory());
		final A accessType = BufferDataAccessFactory.get(typeFactory);
		final CachedCellImg<T, A> img = new CachedCellImg<>(grid, type, cache, accessType);
		img.setLinkedType(typeFactory.createLinkedType(img));
		return img;
	}


	// ========================================================================
 	// private implementation
	// ========================================================================

	private static class ShmCell<A> extends Cell<A> {

		private final NDArray ndArray;

		public ShmCell(final CellDimensionsAndSteps dims, final long[] min, final A data, final NDArray ndArray) {
			super(dims, min, data);
			this.ndArray = ndArray;
		}

		public NDArray ndArray() {
			return ndArray;
		}
	}


	private static class ShmCellLoader<T extends NativeType<T>, A extends BufferAccess<?>> implements CacheLoader<Long, Cell<A>> {

		private final NDArrayFactory ndArrayFactory;

		private final CellGrid grid;
		private final RandomAccessible<? extends Cell<?>> sourceCells;
		private final NDArray.DType dType;
		private final Fraction entitiesPerPixel;
		private final Function<NDArray, A> wrapAsBufferAccess;
		private final SubArrayCopy.Typed<Object, Object> copyArrayToBuffer;

		ShmCellLoader(final AbstractCellImg<T, ?, ?, ?> source, final NDArrayFactory ndArrayFactory) {

			// Create a CacheLoader that copies cells into shared memory buffers
			grid = source.getCellGrid();
			sourceCells = source.getCells();
			this.ndArrayFactory = ndArrayFactory;

			final T type = source.getType();
			dType = DTypes.dtype(type);
			entitiesPerPixel = type.getEntitiesPerPixel();

			wrapAsBufferAccess = createAccessWrapper(dType);
			copyArrayToBuffer = SubArrayCopy.forPrimitiveType(DTypes.primitiveType(dType), false, true);

		}

		@Override
		public Cell<A> get(final Long key) throws Exception {
			final int n = grid.numDimensions();
			final long index = key;

			final long[] gridPos = new long[n];
			grid.getCellGridPositionFlat(index, gridPos);
			final Cell<?> sourceCell = sourceCells.getAt(gridPos);

			final long[] cellMin = new long[n];
			final CellDimensionsAndSteps dimsAndSteps = grid.getCellDimensions(index, cellMin);

			final NDArray ndArray = ndArrayFactory.createNDArray(dType, new NDArray.Shape(F_ORDER, dimsAndSteps.dimensions()));
			final A targetAccess = wrapAsBufferAccess.apply(ndArray);

			final int[] strides = {0};
			final int[] size = {Util.safeInt(entitiesPerPixel.mulCeil(dimsAndSteps.numPixels()))};
			final Object sourceData = ((ArrayDataAccess<?>) sourceCell.getData()).getCurrentStorageArray();
			final Object targetData = targetAccess.getCurrentStorageArray();
			copyArrayToBuffer.copyNDRangeRecursive(0, sourceData, strides, 0, targetData, strides, 0, size);

			return new ShmCell<>(dimsAndSteps, cellMin, targetAccess, ndArray);
		}
	}


	private static class ShmCellRemover<T extends NativeType<T>, A extends BufferAccess<?>> implements CacheRemover<Long, Cell<A>, NDArray> {

		private final CellGrid grid;
		private final Function<NDArray, A> wrapAsBufferAccess;

		ShmCellRemover(final T type, final CellGrid grid) {
			this.grid = grid;
			wrapAsBufferAccess = createAccessWrapper(DTypes.dtype(type));
		}

		@Override
		public void onRemoval(final Long key, final NDArray valueData) {
			valueData.close();
		}

		@Override
		public CompletableFuture<Void> persist(final Long key, final NDArray valueData) {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public NDArray extract(final Cell<A> value) {
			return ((ShmCell<A>) value).ndArray();
		}

		@Override
		public Cell<A> reconstruct(final Long key, final NDArray valueData) {
			final long[] cellMin = new long[grid.numDimensions()];
			final CellDimensionsAndSteps dimsAndSteps = grid.getCellDimensions(key, cellMin);
			final A targetAccess = wrapAsBufferAccess.apply(valueData);
			return new ShmCell<>(dimsAndSteps, cellMin, targetAccess, valueData);
		}
	}


	@SuppressWarnings("unchecked")
	private static <A extends BufferAccess<?>> Function<NDArray, A> createAccessWrapper(final NDArray.DType dType) {
		final PrimitiveType primitiveType = DTypes.primitiveType(dType);
		switch (primitiveType) {
		case BYTE:
			return ndArray -> (A) new ByteBufferAccess(ndArray.buffer(), true);
		case CHAR:
			return ndArray -> (A) new CharBufferAccess(ndArray.buffer());
		case SHORT:
			return ndArray -> (A) new ShortBufferAccess(ndArray.buffer());
		case INT:
			return ndArray -> (A) new IntBufferAccess(ndArray.buffer());
		case LONG:
			return ndArray -> (A) new LongBufferAccess(ndArray.buffer());
		case FLOAT:
			return ndArray -> (A) new FloatBufferAccess(ndArray.buffer());
		case DOUBLE:
			return ndArray -> (A) new DoubleBufferAccess(ndArray.buffer());
		case BOOLEAN:
		case UNDEFINED:
		default:
			throw new IllegalArgumentException("Unsupported type: " + primitiveType);
		}
	}
}
