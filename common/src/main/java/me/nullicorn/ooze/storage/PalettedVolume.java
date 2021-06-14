package me.nullicorn.ooze.storage;

import me.nullicorn.ooze.api.storage.BlockVolume;
import me.nullicorn.ooze.api.world.BlockState;

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
public interface PalettedVolume extends BlockVolume {

  /**
   * @return The palette of blocks used by this volume.
   */
  BlockPalette getPalette();

  /**
   * @return An array of integers relating each block's position to its state in the volume's {@link
   * #getPalette() palette}.
   */
  IntArray getStorage();

  /**
   * @return {@code true} if any block in the section is not {@link BlockState#isAir() air}.
   * Otherwise {@code false}.
   */
  boolean isNotEmpty();
}
