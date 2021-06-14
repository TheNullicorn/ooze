package me.nullicorn.ooze.convert.region;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import me.nullicorn.nedit.NBTReader;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.convert.ChunkSource;
import org.jetbrains.annotations.Nullable;

/**
 * A tool for loading chunks from the region directory of a Minecraft world.
 *
 * @author Nullicorn
 */
public class RegionDirectoryChunkLoader implements ChunkSource {

  private final File                                  directory;
  private final ConcurrentMap<Location2D, RegionFile> loadedRegions;

  public RegionDirectoryChunkLoader(File regionDir) {
    if (!regionDir.isDirectory()) {
      throw new IllegalArgumentException(
          "Invalid region directory: " + regionDir.getAbsolutePath());
    }

    directory = regionDir;
    loadedRegions = new ConcurrentHashMap<>();
  }

  /**
   * Reset this chunk loader to its initial state, potentially freeing up memory.
   *
   * @throws IOException If any region threw an IO exception while being unloaded. If multiple do,
   *                     the first one is thrown.
   */
  public void reset() throws IOException {
    IOException thrownInLoop = null;

    for (RegionFile region : loadedRegions.values()) {
      try {
        region.close();
      } catch (IOException exception) {
        // Ensure that the channels used by subsequent regions get closed.
        if (thrownInLoop == null) {
          thrownInLoop = exception;
        }
      }
    }

    // Clear the region file cache.
    loadedRegions.clear();

    if (thrownInLoop != null) {
      throw thrownInLoop;
    }
  }

  @Override
  @Nullable
  public NBTCompound loadChunk(Location2D chunkLocation) throws IOException {
    return loadChunk(chunkLocation.getX(), chunkLocation.getZ());
  }

  @Override
  @Nullable
  public NBTCompound loadChunk(int chunkX, int chunkZ) throws IOException {
    Location2D regionLocation = new Location2D(
        (int) Math.floor(chunkX / 32.0),
        (int) Math.floor(chunkZ / 32.0));

    // Use cached region.
    RegionFile region = loadedRegions.get(regionLocation);
    if (region != null) {
      return region.loadChunk(chunkX, chunkZ);
    }

    // Load a new region.
    region = loadRegion(regionLocation);
    if (region != null) {
      return region.loadChunk(chunkX, chunkZ);
    }

    // Attempt to load this chunk from an external file.
    return loadOversized(chunkX, chunkZ);
  }

  /**
   * Attempts to read an entire region file from disk.
   *
   * @param regionLocation The coordinates of the region.
   * @return The loaded region file, or null if none exists for the provided coordinates.
   * @throws IOException If the region file could not be read.
   */
  // Suppressed because we don't want to auto-close the region file; it's being stored.
  @SuppressWarnings("java:S2095")
  public @Nullable RegionFile loadRegion(Location2D regionLocation) throws IOException {
    RegionFile region = loadedRegions.get(regionLocation);

    // Load the region if it isn't already loaded.
    if (region == null) {
      File regionFile = new File(directory, LevelFileType.ANVIL.getFileName(regionLocation));
      if (!regionFile.isFile()) {
        // Fall-back to region file if no anvil file exists.
        regionFile = new File(directory, LevelFileType.REGION.getFileName(regionLocation));
        if (!regionFile.isFile()) {
          return null;
        }
      }

      // Load & cache the region.
      region = new RegionFile(regionFile);
      region.open();
      loadedRegions.put(regionLocation, region);
    }

    return region;
  }

  /**
   * Attempts to load an oversized chunk from its separate file.
   *
   * @param chunkX The X coordinate of the chunk.
   * @param chunkZ The Z coordinate of the chunk.
   * @return The serialized NBT data for the chunk, or null if its file could not be found.
   * @throws IOException If the chunk file could not be read.
   */
  @Nullable
  private NBTCompound loadOversized(int chunkX, int chunkZ) throws IOException {
    File chunkFile = new File(directory, LevelFileType.CHUNK.getFileName(chunkX, chunkZ));

    if (chunkFile.isFile()) {
      byte[] chunkData = Files.readAllBytes(chunkFile.toPath());
      return NBTReader.read(new ByteArrayInputStream(chunkData), true, true);
    }

    return null;
  }

  @Override
  public void close() throws IOException {
    reset();
  }
}
