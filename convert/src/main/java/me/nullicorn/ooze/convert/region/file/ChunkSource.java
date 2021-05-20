package me.nullicorn.ooze.convert.region.file;

import java.io.Closeable;
import java.io.IOException;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.ooze.Location2D;
import org.jetbrains.annotations.Nullable;

/**
 * Something that chunks can be loaded from.
 *
 * @author Nullicorn
 */
public interface ChunkSource extends Closeable {

  /**
   * Same as {@link #loadChunk(int, int)}, but with {@code chunkX} and {@code chunkZ} provided via a
   * {@link Location2D}.
   *
   * @see #loadChunk(int, int)
   */
  @Nullable
  default NBTCompound loadChunk(Location2D chunkLocation) throws IOException {
    return loadChunk(chunkLocation.getX(), chunkLocation.getZ());
  }

  /**
   * Loads the NBT data for a chunk at a given pair of coordinates.
   *
   * @param chunkX The x-coordinate of the chunk.
   * @param chunkZ The z-coordinate of the chunk.
   * @return The serialized chunk data, or null if none exists for that chunk.
   * @throws IOException If the chunk could not be accessed or read from its source.
   */
  @Nullable NBTCompound loadChunk(int chunkX, int chunkZ) throws IOException;
}
