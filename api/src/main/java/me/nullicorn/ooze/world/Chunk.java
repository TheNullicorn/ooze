package me.nullicorn.ooze.world;

import me.nullicorn.ooze.serialize.OozeSerializable;
import me.nullicorn.ooze.Location2D;

/**
 * @author Nullicorn
 */
public interface Chunk extends OozeSerializable {

  int getDataVersion();

  Location2D getLocation();

  boolean isEmpty();
}
