package me.nullicorn.ooze.world;

import me.nullicorn.ooze.BlockVolume;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.serialize.OozeSerializable;

/**
 * A 16x256x16 volume of blocks inside a Minecraft world.
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
