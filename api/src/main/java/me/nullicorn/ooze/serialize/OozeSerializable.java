package me.nullicorn.ooze.serialize;

import java.io.IOException;

/**
 * Data that can be serialized into the Ooze format.
 *
 * @author Nullicorn
 */
public interface OozeSerializable {

  /**
   * Serializes the object's contents into the provided stream using the Ooze format.
   */
  void serialize(OozeDataOutputStream out) throws IOException;
}
