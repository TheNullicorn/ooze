package me.nullicorn.ooze.world;

import java.util.Collection;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.serialize.OozeSerializable;
import org.jetbrains.annotations.Nullable;

/**
 * Storage for a Minecraft world with hard boundaries on the X and Z axes.
 *
 * @author Nullicorn
 */
public interface BoundedLevel extends OozeSerializable {

  /**
   * @return The lowest X coordinate of any chunk in the level.
   */
  int getLowestChunkX();

  /**
   * @return The lowest Z coordinate of any chunk in the level.
   */
  int getLowestChunkZ();

  /**
   * @return The size of the level along the X axis (in chunks).
   */
  int getWidth();

  /**
   * @return The size of the level along the Z axis (in chunks).
   */
  int getDepth();

  /**
   * Same as {@link #getChunkAt(int, int)}, but with {@code chunkX} and {@code chunkZ} provided via
   * a {@link Location2D}.
   *
   * @see #getChunkAt(int, int)
   */
  @Nullable
  default Chunk getChunkAt(Location2D chunkLocation) {
    return getChunkAt(chunkLocation.getX(), chunkLocation.getZ());
  }

  /**
   * @param chunkX The x coordinate of the chunk.
   * @param chunkZ The x coordinate of the chunk.
   * @return The data for the chunk at the provided coordinates, or null if that chunk has no data.
   */
  @Nullable
  Chunk getChunkAt(int chunkX, int chunkZ);

  /**
   * @return All chunks in the level that have data associated with them.
   */
  Collection<Chunk> getStoredChunks();

  /**
   * @return All entities that exist in the world represented by this level. Though atypical, it is
   * possible for entities in this list to exist outside the level's bounds.
   */
  NBTList getEntities();

  /**
   * Same as {@link #getEntities(int, int)}, but {@code chunkX} and {@code chunkZ} are provided via
   * a {@link Location2D}.
   *
   * @see #getEntities(int, int)
   */
  default NBTList getEntities(Location2D chunkLocation) {
    return getEntities(chunkLocation.getX(), chunkLocation.getZ());
  }

  /**
   * @return All entities within the bounds of the chunk at the given x and z chunk coordinates.
   */
  NBTList getEntities(int chunkX, int chunkZ);

  /**
   * @return All entities that exist in the world represented by this level. Though atypical, it is
   * possible for blocks in this list to exist outside the level's bounds.
   */
  NBTList getBlockEntities();

  /**
   * Same as {@link #getBlockEntities(int, int)}, but {@code chunkX} and {@code chunkZ} are
   * provided via a {@link Location2D}.
   *
   * @see #getBlockEntities(int, int)
   */
  default NBTList getBlockEntities(Location2D chunkLocation) {
    return getBlockEntities(chunkLocation.getX(), chunkLocation.getZ());
  }

  /**
   * @return All block entities within the bounds of the chunk at the given x and z chunk
   * coordinates.
   */
  NBTList getBlockEntities(int chunkX, int chunkZ);

  /**
   * @return An object for storing persistent, custom data associated with the level.
   */
  NBTCompound getCustomStorage();

  /**
   * Set the data for a chunk in the level, overwriting any existing data for that chunk.
   *
   * @throws IllegalArgumentException If the chunk cannot be stored within the bounds of the level.
   */
  void storeChunk(Chunk chunk);
}
