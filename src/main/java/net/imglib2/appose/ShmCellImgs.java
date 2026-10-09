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
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.CellGrid.CellDimensionsAndSteps;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.util.Cast;
import net.imglib2.util.Fraction;
import net.imglib2.util.Util;
import org.apposed.appose.NDArray;

import static org.apposed.appose.NDArray.Shape.Order.F_ORDER;

public class ShmCellImgs {

	/**
	 * Allocates the {@code NDArray} for each cell of a {@code ShmCellImg}.
	 */
	public interface NDArrayFactory {

		NDArray createNDArray(final NDArray.DType dType, final NDArray.Shape shape);
	}

	/**
	 * Wrap a source {@code CellImg} into a NDArray backed {@code CachedCellImg},
	 * whose cells are copied lazily into the service's managed shared memory.
	 * <p>
	 * Each cell's {@code NDArray} can thus be shared with any number of worker
	 * processes in place. It is closed once the cell is evicted from the
	 * cache, and freed once no worker uses it anymore either. See
	 * {@link NDArray#managed}.
	 * </p>
	 * <p>
	 * The cells of {@code source} must be backed by primitive arrays (i.e.
	 * {@link ArrayDataAccess}).
	 * </p>
	 */
	public static <T extends NativeType<T>, A extends ArrayDataAccess<A> & BufferAccess<A>> CachedCellImg<T, A> createShmCellImg(
			final AbstractCellImg<T, ?, ?, ?> source) {
		return createShmCellImg(source, NDArray::managed);
	}

	/**
	 * Wrap a source {@code CellImg} into a NDArray backed {@code CachedCellImg},
	 * whose cells are copied lazily into {@code NDArray}s allocated by the
	 * given factory. Each cell's {@code NDArray} is closed once the cell is
	 * evicted from the cache.
	 * <p>
	 * The cells of {@code source} must be backed by primitive arrays (i.e.
	 * {@link ArrayDataAccess}).
	 * </p>
	 */
	public static <T extends NativeType<T>, A extends ArrayDataAccess<A> & BufferAccess<A>> CachedCellImg<T, A> createShmCellImg(
			final AbstractCellImg<T, ?, ?, ?> source,
			final NDArrayFactory ndArrayFactory) {

		final T type = source.getType();
		final CellGrid grid = source.getCellGrid();

		final NativeTypeFactory<T, A> typeFactory = Cast.unchecked(type.getNativeTypeFactory());
		final A accessType = BufferDataAccessFactory.get(typeFactory);
		final Function<NDArray, A> wrapAsBufferAccess = ndArray -> accessType.newInstance(ndArray.buffer(), true);

		// Create a CacheLoader that copies cells into NDArray buffers
		final CacheLoader<Long, Cell<A>> loader = new ShmCellLoader<>(source, ndArrayFactory, wrapAsBufferAccess);

		// Create a CacheRemover that closes NDArray of discarded ShmCells.
		final CacheRemover<Long, Cell<A>, NDArray> remover = new ShmCellRemover<>(grid, wrapAsBufferAccess);

		final LoaderRemoverCache<Long, Cell<A>, NDArray> loaderRemoverCache = new GuardedStrongRefLoaderRemoverCache<>(0);
		final Cache<Long, Cell<A>> cache = loaderRemoverCache.withRemover(remover).withLoader(loader);

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

		ShmCellLoader(final AbstractCellImg<T, ?, ?, ?> source, final NDArrayFactory ndArrayFactory, final Function<NDArray, A> wrapAsBufferAccess) {

			// Create a CacheLoader that copies cells into shared memory buffers
			grid = source.getCellGrid();
			sourceCells = source.getCells();
			this.ndArrayFactory = ndArrayFactory;

			final T type = source.getType();
			dType = DTypes.dtype(type);
			entitiesPerPixel = type.getEntitiesPerPixel();

			this.wrapAsBufferAccess = wrapAsBufferAccess;
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


	private static class ShmCellRemover<A extends BufferAccess<?>> implements CacheRemover<Long, Cell<A>, NDArray> {

		private final CellGrid grid;
		private final Function<NDArray, A> wrapAsBufferAccess;

		ShmCellRemover(final CellGrid grid, final Function<NDArray, A> wrapAsBufferAccess) {
			this.grid = grid;
			this.wrapAsBufferAccess = wrapAsBufferAccess;
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
}
