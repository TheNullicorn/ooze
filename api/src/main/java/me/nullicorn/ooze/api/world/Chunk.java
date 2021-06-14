package me.nullicorn.ooze.api.world;

import me.nullicorn.ooze.api.serialize.OozeSerializable;
import me.nullicorn.ooze.api.storage.BlockVolume;

/**
 * A column of blocks inside a Minecraft world, whose width and depth are both 16 blocks, and whose
 * height is a multiple of 16 blocks.
 *
 * @author Nullicorn
 */
public interface Chunk extends BlockVolume, OozeSerializable {

  /**
   * @return The Minecraft <a href=https://minecraft.fandom.com/wiki/Data_version>data version</a>
   * that this chunk's data was created in.
   */
  int getDataVersion();

  /**
   * @return The chunk's absolute position in the world, measured in 16x16 units.
   */
  Location2D getLocation();

  /**
   * @return Whether or not every block in the chunk {@link BlockState#isAir() is air}.
   */
  boolean isEmpty();
}
