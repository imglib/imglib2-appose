package net.imglib2.appose;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.ShortType;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

public class CellPlayground {

	public static void main(String[] args) throws Exception {

		// Open source image as a (array-backed) CellImg
		final N5Reader n5 = new N5FSReader("/Users/pietzsch/workspace/data/111010_weber_full.n5", true);
		final CachedCellImg<ShortType, ?> source = N5Utils.open(n5, "/t00001/s00/s0");

		final Img<ShortType> img = ShmCellImgs.createShmCellImg(source);

		// Read pixels from both original and copied image to verify that they have the same value
		final int[] pos = {0, 0, 20};
		for (int y = 150; y < 350; y += 32) {
			pos[1] = y;
			for (int x = 400; x < 600; x += 32) {
				pos[0] = x;
				final ShortType expected = source.getAt(pos);
				final ShortType actual = img.getAt(pos);
				if ( expected.get() != actual.get() )
					throw new IllegalStateException("expected = " + expected + "actual = " + actual);
			}

			// NB: If you put a breakpoint in NDArray.close(), you can
			// see that ShmCells are evicted from the cache and the
			// CacheRemover closes their NDArray.
			System.gc();
			Thread.sleep(100);
		}
	}

}
