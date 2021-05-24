package me.nullicorn.ooze.world;

import me.nullicorn.ooze.Location2D;

/**
 * Thrown to indicate that a chunk exists outside the bounds of a level.
 *
 * @author Nullicorn
 * @see Chunk
 * @see BoundedLevel
 */
public class ChunkOutOfBoundsException extends Exception {

  public ChunkOutOfBoundsException() {
  }

  public ChunkOutOfBoundsException(Location2D chunkLocation) {
    this("Chunk at " + chunkLocation + "is outside the bounds of the world");
  }

  public ChunkOutOfBoundsException(String message) {
    super(message);
  }

  public ChunkOutOfBoundsException(String message, Throwable cause) {
    super(message, cause);
  }

  public ChunkOutOfBoundsException(Throwable cause) {
    super(cause);
  }
}
