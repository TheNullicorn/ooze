package me.nullicorn.ooze.convert;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.serialize.nbt.ChunkCodec;
import me.nullicorn.ooze.serialize.nbt.ChunkCodec.PooledSectionCodecProvider;
import me.nullicorn.ooze.world.OozeChunk;
import me.nullicorn.ooze.world.OozeLevel;

/**
 * A tool for constructing levels by allowing chunks to be added individually or in groups.
 *
 * @author Nullicorn
 */
public class LevelBuilder {

  // TODO: 5/21/21 Make entities toggleable.
  // TODO: 5/21/21 Provide access to "custom" NBT data.

  @Getter
  protected final ChunkSource     source;
  protected final Set<Location2D> chunksToLoad;

  public LevelBuilder(ChunkSource source) {
    this.source = source;
    chunksToLoad = new HashSet<>();
  }

  /**
   * Adds the chunk at chunk coordinates ({@code chunkX}, {@code chunkZ}) to the world, if it exists
   * in the {@link #getSource() source}.
   */
  @SuppressWarnings("UnusedReturnValue")
  public LevelBuilder addChunk(int chunkX, int chunkZ) {
    return addChunk(new Location2D(chunkX, chunkZ));
  }

  /**
   * Adds the chunk at the provided {@code chunkLoc} to the world, if it exists in the {@link
   * #getSource() source}.
   */
  public LevelBuilder addChunk(Location2D chunkLoc) {
    chunksToLoad.add(chunkLoc);
    return this;
  }

  /**
   * Same as {@link #addChunks(int, int, int, int)}, but {@code minChunkX} and {@code minChunkZ} are
   * provided via a single location, {@code minChunkLoc}.
   *
   * @see #addChunks(int, int, int, int)
   */
  public LevelBuilder addChunks(Location2D minChunkLoc, int width, int depth) {
    return addChunks(minChunkLoc.getX(), minChunkLoc.getZ(), width, depth);
  }

  /**
   * Adds all existing chunks in a {@code width * depth} area to the world.
   *
   * @param minChunkX The lowest possible X coordinate of any chunk that should be added; measured
   *                  in chunks.
   * @param minChunkZ The lowest possible Z coordinate of any chunk that should be added; measured
   *                  in chunks.
   */
  public LevelBuilder addChunks(int minChunkX, int minChunkZ, int width, int depth) {
    int maxChunkX = minChunkX + width;
    int maxChunkZ = minChunkZ + depth;
    for (int chunkX = minChunkX; chunkX < maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ < maxChunkZ; chunkZ++) {
        addChunk(chunkX, chunkZ);
      }
    }
    return this;
  }

  /**
   * Compiles all requested chunks into a single level.
   *
   * @throws IOException           If chunk data could not be retrieved from the {@link #getSource()
   *                               source}, or if any of the chunk data was corrupted.
   * @throws IllegalStateException If the location of any requested chunk lies outside the level's
   *                               bounds.
   */
  public OozeLevel build() throws IOException {
    OozeLevel level = new OozeLevel();
    ChunkCodec chunkCodec = new ChunkCodec(level, new PooledSectionCodecProvider());

    for (Location2D chunkPos : chunksToLoad) {
      NBTCompound chunkData = source.loadChunk(chunkPos);
      if (chunkData != null) {
        // Decode the chunk.
        OozeChunk chunk = chunkCodec.decode(chunkData);
        if (!chunk.isEmpty()) {
          // Add non-empty chunks to the level.
          level.storeChunk(chunk);
        }
      }
    }

    return level;
  }
}
