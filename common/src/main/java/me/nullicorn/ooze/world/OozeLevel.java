package me.nullicorn.ooze.world;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.nedit.type.TagType;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.serialize.OozeDataOutputStream;
import org.jetbrains.annotations.Nullable;

/**
 * @author Nullicorn
 */
public class OozeLevel implements BoundedLevel {

  // Limitations imposed by the format spec.
  private static final int MAX_WIDTH   = 0xFFFF;
  private static final int MAX_DEPTH   = 0xFFFF;
  private static final int MIN_CHUNK_X = Short.MIN_VALUE;
  private static final int MAX_CHUNK_X = Short.MAX_VALUE;
  private static final int MIN_CHUNK_Z = Short.MIN_VALUE;
  private static final int MAX_CHUNK_Z = Short.MAX_VALUE;

  private final Map<Location2D, Chunk> chunks   = new HashMap<>();
  @Getter
  private final NBTList                entities = new NBTList(TagType.COMPOUND);
  @Getter
  private final NBTList                blockEntities = new NBTList(TagType.COMPOUND);
  @Getter
  private final NBTCompound            customStorage = new NBTCompound();

  // Highest and lowest chunk coordinates.
  // Used to ensure new chunks are in bounds.
  private int lowChunkX  = MAX_CHUNK_X;
  private int highChunkX = MIN_CHUNK_X;
  private int lowChunkZ  = MAX_CHUNK_Z;
  private int highChunkZ = MIN_CHUNK_Z;

  @Override
  public int getLowestChunkX() {
    return lowChunkX;
  }

  @Override
  public int getLowestChunkZ() {
    return lowChunkZ;
  }

  @Override
  public int getWidth() {
    return highChunkX - lowChunkX + 1;
  }

  @Override
  public int getDepth() {
    return highChunkZ - lowChunkZ + 1;
  }

  @Override
  public Collection<Chunk> getStoredChunks() {
    return chunks.values();
  }

  @Override
  public @Nullable Chunk getChunkAt(int chunkX, int chunkZ) {
    return chunks.get(new Location2D(chunkX, chunkZ));
  }

  @Override
  public NBTList getEntities(Location2D chunkLoc) {
    return getEntities(chunkLoc.getX(), chunkLoc.getZ());
  }

  @Override
  public NBTList getEntities(int chunkX, int chunkZ) {
    NBTList entitiesInChunk = new NBTList(TagType.COMPOUND);
    for (Object entity : entities) {
      // Every entity should have a "Pos" list with 3 doubles for their X, Y, and Z coords.
      NBTList pos = ((NBTCompound) entity).getList("Pos");
      if (pos == null) {
        continue;
      }

      // Convert the entity's coords to chunk coords.
      int entityChunkX = (int) Math.floor(pos.getDouble(0) / 16);
      int entityChunkZ = (int) Math.floor(pos.getDouble(2) / 16);
      if (entityChunkX == chunkX && entityChunkZ == chunkZ) {
        entitiesInChunk.add(entity);
      }
    }
    return entitiesInChunk;
  }

  @Override
  public NBTList getBlockEntities(Location2D chunkLoc) {
    return getBlockEntities(chunkLoc.getX(), chunkLoc.getZ());
  }

  @Override
  public NBTList getBlockEntities(int chunkX, int chunkZ) {
    NBTList blockEntitiesInChunk = new NBTList(TagType.COMPOUND);
    for (Object blockEntity : blockEntities) {
      NBTCompound blockData = (NBTCompound) blockEntity;
      // Every block entity should have ints "x", "y", and "z" for its coordinates.
      if (!blockData.containsKey("x") || !blockData.containsKey("z")) {
        continue;
      }
      int blockX = blockData.getInt("x", 0);
      int blockZ = blockData.getInt("z", 0);

      // Convert the block's coords to chunk coords.
      int blockChunkX = (int) Math.floor(blockX / 16.0);
      int blockChunkZ = (int) Math.floor(blockZ / 16.0);
      if (blockChunkX == chunkX && blockChunkZ == chunkZ) {
        blockEntitiesInChunk.add(blockEntity);
      }
    }
    return blockEntitiesInChunk;
  }

  @Override
  public void storeChunk(Chunk chunk) {
    if (!isChunkInBounds(chunk.getLocation())) {
      throw new IllegalArgumentException("Chunk at " + chunk.getLocation() + " is out of bounds");
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
  public void serialize(OozeDataOutputStream out) throws IOException {
    int width = getWidth();
    int depth = getDepth();

    out.writeHeader();

    // Write world size & location.
    out.writeShort(lowChunkX);
    out.writeShort(lowChunkZ);
    out.writeShort(width);
    out.writeShort(depth);

    // Generate the chunk mask.
    Chunk[] chunksToWrite = new Chunk[width * depth];
    BitSet chunkMask = new BitSet(chunksToWrite.length);
    chunks.values().forEach(chunk -> {
      if (!chunk.isEmpty()) {
        int chunkX = chunk.getLocation().getX();
        int chunkZ = chunk.getLocation().getZ();

        int chunkIndex = ((chunkX - lowChunkX) * getDepth()) + (chunkZ - lowChunkZ);
        chunkMask.set(chunkIndex, true);
        chunksToWrite[chunkIndex] = chunk;
      }
    });
    out.writeBitSet(chunkMask, (int) Math.ceil(width * depth / (double) Byte.SIZE));

    // Compress & write chunk data in order.
    ByteArrayOutputStream chunkBytesOut = new ByteArrayOutputStream();
    OozeDataOutputStream chunkDataOut = new OozeDataOutputStream(chunkBytesOut);
    for (Chunk chunk : chunksToWrite) {
      if (chunk != null) {
        chunk.serialize(chunkDataOut);
      }
    }
    out.writeCompressed(chunkBytesOut.toByteArray());

    // Write NBT extras (entities, block entities, and custom data).
    out.writeOptionalNBT(!blockEntities.isEmpty(), "BlockEntities", blockEntities);
    out.writeOptionalNBT(!entities.isEmpty(), "Entities", entities);
    out.writeOptionalNBT(!customStorage.isEmpty(), "Custom", customStorage);
  }

  /**
   * @return Whether or not Ooze's format limitations allow a chunk to be stored at the given
   * location.
   */
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
}
