package me.nullicorn.ooze.storage;

import me.nullicorn.ooze.serialize.IntArray;
import me.nullicorn.ooze.world.BlockState;

/**
 * A three-dimensional area of blocks that uses a {@link BlockPalette palette} to associate each
 * block's position with its state.
 * <p>
 * <strong>WARNING:</strong> Direct modification of either the {@link #getPalette() palette} or
 * {@link #getStorage() storage container} may cause unwaned side effects for the volume as a
 * whole.
 *
 * @author Nullicorn
 */
public interface PalettedVolume<P extends BlockPalette, S extends IntArray> {

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
   * @return The lowest x-coordinate that any block in the volume may have.
   */
  int getMinX();

  /**
   * @return The lowest y-coordinate that any block in the volume may have.
   */
  int getMinY();

  /**
   * @return The lowest z-coordinate that any block in the volume may have.
   */
  int getMinZ();

  /**
   * @return The size of the volume along the x-axis, in blocks.
   */
  int getWidth();

  /**
   * @return The size of the volume along the y-axis, in blocks.
   */
  int getHeight();

  /**
   * @return The size of the volume along the z-axis, in blocks.
   */
  int getDepth();

  /**
   * @return The state of the block at the provided coordinates.
   * @throws IndexOutOfBoundsException If the provided coordinates are outside the bounds of this
   *                                   volume.
   */
  BlockState getBlockAt(int x, int y, int z);

  /**
   * Changes the state of the block at a given position.
   *
   * @return The block state that was previously set at those coordinates.
   * @throws IndexOutOfBoundsException If the provided coordinates are outside the bounds of this
   *                                   volume.
   */
  BlockState setBlockAt(int x, int y, int z, BlockState state);
}
