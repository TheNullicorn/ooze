package me.nullicorn.ooze.api.world;

import java.util.Collection;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.nedit.type.TagType;
import me.nullicorn.ooze.api.storage.BlockVolume;
import org.jetbrains.annotations.Nullable;

/**
 * Storage for a Minecraft world with hard boundaries on the X and Z axes.
 *
 * @author Nullicorn
 */
public interface BoundedLevel<C extends Chunk> extends BlockVolume {

  /**
   * @return The lowest X and Z coordinates of any chunk in the level.
   */
  Location2D getLowestChunkPos();

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
  default C getChunkAt(Location2D chunkLocation) {
    return getChunkAt(chunkLocation.getX(), chunkLocation.getZ());
  }

  /**
   * @param chunkX The x coordinate of the chunk.
   * @param chunkZ The x coordinate of the chunk.
   * @return The data for the chunk at the provided coordinates, or null if that chunk has no data.
   */
  @Nullable
  C getChunkAt(int chunkX, int chunkZ);

  /**
   * Set the data for a chunk in the level, overwriting any existing data for that chunk.
   *
   * @throws IllegalArgumentException If the chunk cannot be stored within the bounds of the level.
   */
  void storeChunk(C chunk);

  /**
   * @return All chunks in the level that have data associated with them.
   */
  Collection<C> getStoredChunks();

  /**
   * @return An object for storing persistent, custom data associated with the level.
   */
  NBTCompound getCustomStorage();

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
   * Same as {@link #setEntities(int, int, NBTList)}, but {@code chunkX} and {@code chunkZ} are
   * provided via a {@link Location2D}.
   *
   * @see #setEntities(int, int, NBTList)
   */
  default void setEntities(Location2D chunkLoc, NBTList replacement) {
    setEntities(chunkLoc.getX(), chunkLoc.getZ(), replacement);
  }

  /**
   * Replaces all entities previously stored in the chunk at {@code (chunkX, chunkZ)} with the
   * entities in the {@code replacement} list. If the {@code replacement} list is empty, then any
   * existing entities in that chunk are cleared, and none are added.
   *
   * @param replacement Any entities that should be stored in the chunk. Must be a list of compound
   *                    tags, unless the list is empty (in which case it can be anything).
   * @throws IllegalArgumentException If the {@code replacement} list has entries but its {@link
   *                                  NBTList#getContentType() contentType} is not {@link
   *                                  TagType#COMPOUND COMPOUND}.
   */
  void setEntities(int chunkX, int chunkZ, NBTList replacement);

  /**
   * @return All entities that exist in the world represented by this level. Though atypical, it is
   * possible for blocks in this list to exist outside the level's bounds.
   */
  NBTList getBlockEntities();

  /**
   * Same as {@link #getBlockEntities(int, int)}, but {@code chunkX} and {@code chunkZ} are provided
   * via a {@link Location2D}.
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
   * Same as {@link #setBlockEntities(int, int, NBTList)}, but {@code chunkX} and {@code chunkZ} are
   * provided via a {@link Location2D}.
   *
   * @see #setBlockEntities(int, int, NBTList)
   */
  default void setBlockEntities(Location2D chunkLoc, NBTList replacement) {
    setBlockEntities(chunkLoc.getX(), chunkLoc.getZ(), replacement);
  }

  /**
   * Replaces all block entities previously stored in the chunk at {@code (chunkX, chunkZ)} with the
   * block entities in the {@code replacement} list. If the {@code replacement} list is empty, then
   * any existing block entities in that chunk are cleared, and none are added.
   *
   * @param replacement Any block entities that should be stored in the chunk. Must be a list of
   *                    compound tags, unless the list is empty (in which case it can be anything).
   * @throws IllegalArgumentException If the {@code replacement} list has entries but its {@link
   *                                  NBTList#getContentType() contentType} is not {@link
   *                                  TagType#COMPOUND COMPOUND}.
   */
  void setBlockEntities(int chunkX, int chunkZ, NBTList replacement);
}
