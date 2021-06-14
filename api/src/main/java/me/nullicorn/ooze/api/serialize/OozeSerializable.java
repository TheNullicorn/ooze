package me.nullicorn.ooze.api.serialize;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Data that can be serialized into the Ooze format.
 *
 * @author Nullicorn
 */
public interface OozeSerializable {

  /**
   * Serializes the object's contents into the provided stream in a format compatible with Ooze.
   */
  void serialize(DataOutputStream out) throws IOException;
}
