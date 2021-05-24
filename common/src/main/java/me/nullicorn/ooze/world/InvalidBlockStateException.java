package me.nullicorn.ooze.world;

import me.nullicorn.nedit.type.NBTCompound;

/**
 * Thrown to indicate that a block state cannot be used.
 *
 * @author Nullicorn
 * @see BlockState
 */
public class InvalidBlockStateException extends Exception {

  public InvalidBlockStateException() {
  }

  public InvalidBlockStateException(NBTCompound rawBlockState) {
    this("Unable to deserialize block state: " + rawBlockState);
  }

  public InvalidBlockStateException(String message) {
    super(message);
  }

  public InvalidBlockStateException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidBlockStateException(Throwable cause) {
    super(cause);
  }
}
