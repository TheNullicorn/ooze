package me.nullicorn.ooze.world;

import me.nullicorn.ooze.BlockVolume;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.serialize.OozeSerializable;

/**
 * @author Nullicorn
 */
public interface Chunk extends BlockVolume, OozeSerializable {

  int getDataVersion();

  Location2D getLocation();

  boolean isEmpty();
}
