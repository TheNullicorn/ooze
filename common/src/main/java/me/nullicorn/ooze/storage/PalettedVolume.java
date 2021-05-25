package me.nullicorn.ooze.storage;

import me.nullicorn.ooze.BlockVolume;
import me.nullicorn.ooze.serialize.IntArray;
import me.nullicorn.ooze.world.BlockState;

/**
 * A volume of blocks that uses a {@link BlockPalette palette} to associate each block's position
 * with its state.
 * <p>
 * <strong>WARNING:</strong> Direct modification of either the {@link #getPalette() palette} or
 * {@link #getStorage() storage container} may cause unwanted side effects for the volume as a
 * whole.
 *
 * @author Nullicorn
 */
public interface PalettedVolume<P extends BlockPalette, S extends IntArray> extends BlockVolume {

  /**
   * @return The palette of blocks used by this volume.
   */
  P getPalette();

  /**
   * @return An array of integers relating each block's position to its state in the volume's {@link
   * #getPalette() palette}.
   */
  S getStorage();

  /**
   * @return Whether or not all blocks in the volume {@link BlockState#isAir() are air}.
   */
  boolean isEmpty();
}
