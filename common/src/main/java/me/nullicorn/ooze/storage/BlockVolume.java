package me.nullicorn.ooze.storage;

import me.nullicorn.ooze.world.BlockState;

/**
 * A three-dimensional region of blocks.
 *
 * @author Nullicorn
 */
public interface BlockVolume {

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
   * @return Whether or not the provided coordinates are within the boundaries of the volume.
   */
  default boolean isInBounds(int x, int y, int z) {
    int minX = getMinX();
    int minY = getMinX();
    int minZ = getMinX();

    return x >= getMinX()
           && y >= getMinY()
           && z >= getMinZ()
           && x < minX + getWidth()
           && y < minY + getHeight()
           && z < minZ + getDepth();
  }

  /**
   * @return The state of the block at the provided coordinates.
   * @throws IndexOutOfBoundsException If the provided coordinates are outside the bounds of this
   *                                   volume.
   */
  BlockState getBlockAt(int x, int y, int z);
}
