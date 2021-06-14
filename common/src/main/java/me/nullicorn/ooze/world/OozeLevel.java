package me.nullicorn.ooze.world;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.nedit.type.TagType;
import me.nullicorn.ooze.api.world.Location2D;
import me.nullicorn.ooze.api.world.BlockState;
import me.nullicorn.ooze.api.world.BoundedLevel;
import org.jetbrains.annotations.Nullable;

/**
 * @author Nullicorn
 */
public class OozeLevel implements BoundedLevel<OozeChunk> {

  // Limitations imposed by the format spec.
  private static final int MAX_WIDTH   = 0xFFFF;
  private static final int MAX_DEPTH   = 0xFFFF;
  private static final int MIN_CHUNK_X = Short.MIN_VALUE;
  private static final int MAX_CHUNK_X = Short.MAX_VALUE;
  private static final int MIN_CHUNK_Z = Short.MIN_VALUE;
  private static final int MAX_CHUNK_Z = Short.MAX_VALUE;

  // All chunks in the level, mapped by their x and z chunk coordinates (16 blocks per unit).
  private final Map<Location2D, OozeChunk> chunks;

  // The highest and lowest chunk coordinates in the level. These are initialized the opposite way
  // (e.g. low is set to MAX_CHUNK) so that the first chunk stored will always have the highest and
  // lowest coordinates on both axes.
  private int lowChunkX;
  private int highChunkX;
  private int lowChunkZ;
  private int highChunkZ;

  // NBT storage.
  private final NBTList     entities;
  private final NBTList     blockEntities;
  private final NBTCompound customStorage;

  public OozeLevel() {
    chunks = new HashMap<>();
    lowChunkX = MAX_CHUNK_X;
    highChunkX = MIN_CHUNK_X;
    lowChunkZ = MAX_CHUNK_X;
    highChunkZ = MIN_CHUNK_X;

    entities = new NBTList(TagType.COMPOUND);
    blockEntities = new NBTList(TagType.COMPOUND);
    customStorage = new NBTCompound();
  }

  // Chunk accessors.

  @Override
  public @Nullable OozeChunk getChunkAt(Location2D chunkLocation) {
    return chunks.get(chunkLocation);
  }

  @Override
  public @Nullable OozeChunk getChunkAt(int chunkX, int chunkZ) {
    return chunks.get(new Location2D(chunkX, chunkZ));
  }

  @Override
  public void storeChunk(OozeChunk chunk) {
    if (chunk == null) {
      throw new IllegalArgumentException("Cannot store null chunk in level");
    } else if (!isChunkInBounds(chunk.getLocation())) {
      throw new IllegalArgumentException("Chunk " + chunk.getLocation() + " is out of bounds");
    }

    int chunkX = chunk.getLocation().getX();
    int chunkZ = chunk.getLocation().getZ();

    // Update the level's bounds accordingly.
    if (chunkX < lowChunkX) {
      lowChunkX = chunkX;
    }
    if (chunkX > highChunkX) {
      highChunkX = chunkX;
    }
    if (chunkZ < lowChunkZ) {
      lowChunkZ = chunkZ;
    }
    if (chunkZ > highChunkZ) {
      highChunkZ = chunkZ;
    }

    chunks.put(chunk.getLocation(), chunk);
  }

  @Override
  public Collection<OozeChunk> getStoredChunks() {
    // Prevents direct modification of the level's chunk storage.
    return Collections.unmodifiableCollection(chunks.values());
  }

  /**
   * @return Whether or not Ooze's format limitations allow a chunk to be stored at the given
   * location.
   */
  // Suppressed for readability.
  @SuppressWarnings({"java:S1871", "RedundantIfStatement"})
  private boolean isChunkInBounds(Location2D chunkLoc) {
    int chunkX = chunkLoc.getX();
    int chunkZ = chunkLoc.getZ();

    if (chunkX < MIN_CHUNK_X
        || chunkX > MAX_CHUNK_X
        || chunkZ < MIN_CHUNK_Z
        || chunkZ > MAX_CHUNK_Z) {
      // Chunk coordinates cannot fit due to format restrictions.
      return false;

    } else if ((chunkX < lowChunkX && highChunkX - chunkX > MAX_WIDTH)) {
      // Chunk surpasses lowest X && expanding would cause an overflow.
      return false;

    } else if (chunkX > highChunkX && chunkX - lowChunkX > MAX_WIDTH) {
      // Chunk surpasses highest X && expanding would cause an overflow.
      return false;

    } else if (chunkZ < lowChunkZ && highChunkZ - chunkZ > MAX_DEPTH) {
      // Chunk surpasses lowest Z && expanding would cause an overflow.
      return false;

    } else if (chunkZ > highChunkZ && chunkZ - lowChunkZ > MAX_DEPTH) {
      // Chunk surpasses highest Z && expanding would cause an overflow.
      return false;
    }

    return true;
  }

  // NBT storage.

  @Override
  public NBTCompound getCustomStorage() {
    return customStorage;
  }

  // Entity accessors.

  @Override
  public NBTList getEntities() {
    return entities;
  }

  @Override
  public NBTList getEntities(int chunkX, int chunkZ) {
    NBTList entitiesInChunk = new NBTList(TagType.COMPOUND);
    entities.forEachCompound(entity -> {
      // Every entity should have a "Pos" list with 3 doubles for their X, Y, and Z coordinates.
      NBTList pos = entity.getList("Pos");

      if (pos == null || pos.size() != 3 || pos.getContentType() != TagType.DOUBLE) {
        return; // continue;
      }

      // Convert the entity's coordinates to chunk coordinates.
      // `0` and `2` refer to the X and Z indices in the list.
      int entityChunkX = (int) Math.floor(pos.getDouble(0) / 16);
      int entityChunkZ = (int) Math.floor(pos.getDouble(2) / 16);
      if (entityChunkX == chunkX && entityChunkZ == chunkZ) {
        entitiesInChunk.add(entity);
      }
    });
    return entitiesInChunk;
  }

  @Override
  public void setEntities(int chunkX, int chunkZ, NBTList replacement) {
    if (replacement == null) {
      throw new IllegalArgumentException("Entity list cannot be null");
    } else if (!replacement.isEmpty() && replacement.getContentType() != TagType.COMPOUND) {
      // Entities (in non-empty lists) must be represented as compounds.
      throw new IllegalArgumentException("Entity data must be " +
                                         TagType.COMPOUND + ", not " +
                                         replacement.getContentType());
    }

    entities.removeAll(getEntities(chunkX, chunkZ));
    if (!replacement.isEmpty()) {
      entities.addAll(replacement);
    }
  }

  // Block entity accessors.

  @Override
  public NBTList getBlockEntities() {
    return blockEntities;
  }

  @Override
  public NBTList getBlockEntities(int chunkX, int chunkZ) {
    NBTList blockEntitiesInChunk = new NBTList(TagType.COMPOUND);
    blockEntities.forEachCompound(blockEntity -> {
      // Every block entity should have ints "x", "y", and "z" for its coordinates.
      Object blockX = blockEntity.get("x");
      Object blockZ = blockEntity.get("z");

      if (!(blockX instanceof Integer) || !(blockZ instanceof Integer)) {
        return; // continue;
      }

      // Convert the block's coordinates into chunk coordinates.
      int blockChunkX = (int) Math.floor((int) blockX / 16.0);
      int blockChunkZ = (int) Math.floor((int) blockZ / 16.0);
      if (blockChunkX == chunkX && blockChunkZ == chunkZ) {
        blockEntitiesInChunk.add(blockEntity);
      }
    });
    return blockEntitiesInChunk;
  }

  @Override
  public void setBlockEntities(int chunkX, int chunkZ, NBTList replacement) {
    if (replacement == null) {
      throw new IllegalArgumentException("Block entity list cannot be null");
    } else if (!replacement.isEmpty() && replacement.getContentType() != TagType.COMPOUND) {
      // Block entities (in non-empty lists) must be represented as compounds.
      throw new IllegalArgumentException("Block entity data must be " +
                                         TagType.COMPOUND + ", not " +
                                         replacement.getContentType());
    }

    blockEntities.removeAll(getBlockEntities(chunkX, chunkZ));
    if (!replacement.isEmpty()) {
      blockEntities.addAll(replacement);
    }
  }

  @Override
  public Location2D getLowestChunkPos() {
    // Default to the world's origin if no chunks exist.
    return chunks.isEmpty()
        ? new Location2D(0, 0)
        : new Location2D(lowChunkX, lowChunkZ);
  }

  @Override
  public int getWidth() {
    return chunks.isEmpty()
        ? 0
        : highChunkX - lowChunkX + 1;
  }

  @Override
  public int getHeight() {
    return 0;
  }

  @Override
  public int getDepth() {
    return chunks.isEmpty()
        ? 0
        : highChunkZ - lowChunkZ + 1;
  }

  @Override
  public int getMinX() {
    return chunks.isEmpty()
        ? 0
        : lowChunkX * OozeChunk.WIDTH;
  }

  @Override
  public int getMinY() {
    if (chunks.isEmpty()) {
      return 0;
    }
    int minY = Integer.MAX_VALUE;

    for (OozeChunk chunk : chunks.values()) {
      int chunkMinY = chunk.getMinY();
      if (chunkMinY < minY) {
        minY = chunkMinY;
      }
    }
    return minY;
  }

  @Override
  public int getMinZ() {
    return chunks.isEmpty()
        ? 0
        : lowChunkZ * OozeChunk.DEPTH;
  }

  @Override
  public BlockState getBlockAt(int x, int y, int z) {
    OozeChunk chunk = getChunkAt(x >> 4, z >> 4);
    if (chunk != null) {
      return chunk.getBlockAt(x & 15, y, z & 15);
    }
    return BlockState.DEFAULT;
  }
}
