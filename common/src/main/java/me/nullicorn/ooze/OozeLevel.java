package me.nullicorn.ooze;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.nedit.type.TagType;
import me.nullicorn.ooze.serialize.OozeDataOutputStream;
import me.nullicorn.ooze.world.BoundedLevel;
import me.nullicorn.ooze.world.Chunk;
import me.nullicorn.ooze.world.ChunkOutOfBoundsException;
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

  private final Map<Location2D, Chunk> chunks        = new HashMap<>();
  @Getter
  private final NBTList                entities      = new NBTList(TagType.COMPOUND);
  @Getter
  private final NBTList                blockEntities = new NBTList(TagType.COMPOUND);
  @Getter
  private final NBTCompound            customStorage = new NBTCompound();

  // Highest and lowest chunk coordinates.
  // Used to ensure new chunks are in bounds.
  private int lowChunkX  = 0;
  private int highChunkX = 0;
  private int lowChunkZ  = 0;
  private int highChunkZ = 0;

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
    return highChunkX - lowChunkX;
  }

  @Override
  public int getDepth() {
    return highChunkZ - lowChunkZ;
  }

  @Override
  public Collection<Chunk> getStoredChunks() {
    return chunks.values();
  }

  @Override
  public @Nullable Chunk getChunkAt(Location2D chunkLocation) {
    return chunks.get(chunkLocation);
  }

  @Override
  public @Nullable Chunk getChunkAt(int chunkX, int chunkZ) {
    return chunks.get(new Location2D(chunkX, chunkZ));
  }

  @Override
  public void storeChunk(Chunk chunk) throws ChunkOutOfBoundsException {
    if (!isChunkInBounds(chunk.getLocation())) {
      throw new ChunkOutOfBoundsException(chunk.getLocation());
    }

    int chunkX = chunk.getLocation().getX();
    int chunkZ = chunk.getLocation().getZ();

    // Update the level's lowest chunk X & Z accordingly.
    if (chunkX < lowChunkX) {
      lowChunkX = chunkX;
    } else if (chunkX > highChunkX) {
      highChunkX = chunkX;
    }

    if (chunkZ < lowChunkZ) {
      lowChunkZ = chunkZ;
    } else if (chunkZ > highChunkZ) {
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
    List<Chunk> writtenChunks = new ArrayList<>();
    BitSet chunkMask = new BitSet(width * depth);
    chunks.values().forEach(chunk -> {
      if (!chunk.isEmpty()) {
        chunkMask.set(calculateChunkMaskIndex(chunk), true);
        writtenChunks.add(chunk);
      }
    });

    // Pad the chunk mask to ceil(width * depth) bits & write it to the stream.
    byte[] chunkMaskBytes = chunkMask.toByteArray();
    byte[] paddedChunkMask = new byte[(int) Math.ceil(width * depth / (double) Byte.SIZE)];
    System.arraycopy(chunkMaskBytes, 0, paddedChunkMask, 0, chunkMaskBytes.length);
    out.write(paddedChunkMask);

    // Sort chunks by order of appearance in the chunk mask.
    writtenChunks.sort(Comparator.comparingInt(this::calculateChunkMaskIndex));

    // Compress & write chunk data in order.
    ByteArrayOutputStream chunkBytesOut = new ByteArrayOutputStream();
    OozeDataOutputStream chunkDataOut = new OozeDataOutputStream(chunkBytesOut);
    for (Chunk chunk : writtenChunks) {
      chunk.serialize(chunkDataOut);
    }
    out.writeCompressed(chunkBytesOut.toByteArray());

    // Write NBT extras (entities, block entities, and custom data).
    out.writeOptionalNBT(!blockEntities.isEmpty(), "tiles", blockEntities);
    out.writeOptionalNBT(!entities.isEmpty(), "entities", entities);
    out.writeOptionalNBT(!customStorage.isEmpty(), "custom", customStorage);
  }

  /**
   * @return The index in the chunk mask where the provided {@code chunk}'s state can be found.
   */
  private int calculateChunkMaskIndex(Chunk chunk) {
    int chunkX = chunk.getLocation().getX();
    int chunkZ = chunk.getLocation().getZ();
    return ((chunkX - lowChunkX) * getDepth()) + (chunkZ - lowChunkZ);
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
