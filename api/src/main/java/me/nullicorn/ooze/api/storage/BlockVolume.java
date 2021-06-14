package me.nullicorn.ooze.api.storage;

import me.nullicorn.ooze.api.world.BlockState;

/**
 * A three-dimensional region of blocks.
 *
 * @author Nullicorn
 */
public interface BlockVolume {

  /**
   * @return The size of the volume along the X axis, in blocks.
   */
  int getWidth();

  /**
   * @return The size of the volume along the Y axis, in blocks.
   */
  int getHeight();

  /**
   * @return The size of the volume along the Z axis, in blocks.
   */
  int getDepth();

  /**
   * @return The lowest X coordinate that any block in the volume may have.
   */
  int getMinX();

  /**
   * @return The lowest Y coordinate that any block in the volume may have.
   */
  int getMinY();

  /**
   * @return The lowest Z coordinate that any block in the volume may have.
   */
  int getMinZ();

  /**
   * @return The state of the block at the provided coordinates.
   * @throws IndexOutOfBoundsException If the provided coordinates are outside the bounds of this
   *                                   volume.
   */
  BlockState getBlockAt(int x, int y, int z);
}
