package me.nullicorn.ooze.serialize;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import me.nullicorn.nedit.NBTWriter;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.ooze.ResourceLocation;
import me.nullicorn.ooze.storage.BitCompactIntArray;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.storage.PalettedVolume;
import me.nullicorn.ooze.world.BlockState;
import me.nullicorn.ooze.world.BoundedLevel;
import me.nullicorn.ooze.world.Chunk;
import me.nullicorn.ooze.world.OozeChunk;

/**
 * @author Nullicorn
 */
// TODO: 6/9/21 Add biome support.
public class OozeDataOutputStream extends DataOutputStream {

  static final int MAGIC_NUMBER   = 0x610BB10B;
  static final int FORMAT_VERSION = 0;

  public OozeDataOutputStream(OutputStream out) {
    super(out);
  }

  /**
   * @return The Ooze format version implemented by this stream.
   */
  public int getFormatVersion() {
    return FORMAT_VERSION;
  }

  /**
   * Writes the standard Ooze header to the underlying output stream; four bytes of magic numbers
   * followed by a 1-byte {@link #getFormatVersion() version number}.
   *
   * @throws IOException If the header cannot be written.
   */
  public void writeHeader() throws IOException {
    writeInt(MAGIC_NUMBER);
    writeVarInt(getFormatVersion());
  }

  /**
   * Serializes the provided object to the underlying output stream.
   *
   * @throws IOException If the object could not be serialized or written.
   */
  public void write(OozeSerializable obj) throws IOException {
    obj.serialize(this);
  }

  /**
   * Writes a variable-length, LEB128-encoded integer to the underlying output stream.
   *
   * @throws IOException If the number could not be written.
   */
  public void writeVarInt(int value) throws IOException {
    // Shortcut; values that fit in 7 bits can be written directly.
    if (value >= 0 && value <= 127) {
      write(value);
      return;
    }

    do {
      int temp = value & 0x7F;
      value >>>= 7;
      if (value != 0) {
        temp |= 0x80;
      }
      write(temp);
    } while (value != 0);
  }

  /**
   * Writes a BitSet to the underlying stream using however bytes are needed to hold {@code
   * bitCount} bits. If the provided BitSet is shorter or longer than the desired {@code bitCount},
   * the tail will be padded with unset bits or trimmed respectively.
   * <p><br>
   * The number of bytes actually written is equal to
   * <pre>(int) Math.ceil((float) bitCount / Byte.SIZE)</pre>
   * If the {@code bitCount} is zero, a single {@code 00} byte is written.
   *
   * @throws IOException If the bytes could not be written.
   */
  public void writeBitSet(BitSet value, int bitCount) throws IOException {
    if (bitCount == 0) {
      out.write(0);
    }

    int bytesNeeded = (int) Math.ceil((float) bitCount / Byte.SIZE);
    byte[] bytes = value.toByteArray();

    // Pad / trim the array to the desired bitCount.
    if (bytes.length != bytesNeeded) {
      byte[] temp = new byte[bytesNeeded];
      System.arraycopy(bytes, 0, temp, 0, Math.min(temp.length, bytes.length));
      bytes = temp;
    }

    write(bytes);
  }

  /**
   * Writes each entry in a block palette to the underlying stream, prefixed by the number of
   * entries as a LEB128-encoded integer.
   * <p><br>
   * The first byte of each entry has two purposes: the highest 7 bits hold the length of the
   * state's {@link BlockState#getName() name} (in bytes), and the lowest bit indicates whether or
   * not the state has any additional {@link BlockState#getProperties() properties}. Following that
   * is the state's {@link BlockState#getName() name}, encoded using however many bytes were
   * indicated previously. Then, if the aforementioned low bit was set, the block's properties are
   * serialized as an unnamed NBT compound.
   *
   * @throws IllegalArgumentException If any state in the palette has a name longer than 127
   *                                  characters when {@link ResourceLocation#toString()} is used.
   * @throws IOException              If the palette could not be written, or if the NBT properties
   *                                  could not be serialized.
   */
  public void writePalette(BlockPalette palette) throws IOException {
    writeVarInt(palette.size());
    for (BlockState state : palette) {
      // Name's length can't use more than 7 bits.
      String name = state.getName().toString();
      if (name.length() > 0b1111111) {
        throw new IllegalArgumentException("State name must be < 127 characters: " + name);
      }

      // Least sig bit indicates whether or not the state has properties.
      // 7 most sig bits are the length of the state's name.
      int length = name.length() << 1;

      NBTCompound properties = state.getProperties();
      boolean hasProperties = (properties != null);
      if (hasProperties) {
        // Set lowest bit of `length` to indicate that properties follow.
        length |= 1;
      }

      write(length);
      writeBytes(name);
      if (hasProperties) {
        NBTWriter.write(state.getProperties(), out, false);
      }
    }
  }

  /**
   * Writes a 1-byte boolean to the underlying stream indicating whether or not NBT data follows. If
   * so, the NBT data is assigned the provided {@code key}, wrapped in a nameless compound and
   * compressed using Zstandard.
   *
   * @param write Whether or not the NBT data should be written.
   * @param key   The key to map the {@code value} to inside the wrapper compound.
   * @param value The NBT value to write conditionally.
   * @throws IOException If the NBT could not be serialized or if any of the data could not be
   *                     written.
   */
  public void writeOptionalNBT(boolean write, String key, Object value) throws IOException {
    writeBoolean(write);

    if (write) {
      NBTCompound root = new NBTCompound();
      root.put(key, value);

      // Serialize & compress the contents of the wrapper compound.
      ByteArrayOutputStream nbtOut = new ByteArrayOutputStream();
      NBTWriter.write(root, nbtOut, false);
      writeCompressed(nbtOut.toByteArray());
    }
  }

  /**
   * Same as {@link #writeCompressed(byte[], int)}, but compression {@code level} defaults to {@code
   * 3}.
   *
   * @see #writeCompressed(byte[], int)
   */
  public void writeCompressed(byte[] data) throws IOException {
    writeCompressed(data, 3);
  }

  /**
   * Compresses the provided {@code data} using Zstandard and writes it to the underlying stream.
   *
   * @param level The level of compression to use, as defined by Zstandard.
   * @throws IOException If the data could not be compressed or written to the stream.
   */
  public void writeCompressed(byte[] data, int level) throws IOException {
    byte[] compressed = Zstd.compress(data, level);

    writeVarInt(compressed.length);
    writeVarInt(data.length);
    write(compressed);
  }

  /**
   * Writes an entire {@code level} to the underlying stream.
   * <p>
   * // TODO: 6/10/21 Document level encoding.
   *
   * @throws IOException If any part of the level could not be written to the stream.
   */
  public void writeLevel(BoundedLevel level) throws IOException {
    int width = level.getWidth();
    int depth = level.getDepth();
    int lowChunkX = level.getLowestChunkX();
    int lowChunkZ = level.getLowestChunkZ();
    NBTList blockEntities = level.getBlockEntities();
    NBTList entities = level.getEntities();
    NBTCompound customStorage = level.getCustomStorage();

    // Generate the chunk mask.
    Chunk[] chunksToWrite = new Chunk[width * depth];
    BitSet chunkMask = new BitSet(chunksToWrite.length);
    level.getStoredChunks().forEach(chunk -> {
      if (!chunk.isEmpty()) {
        int chunkX = chunk.getLocation().getX();
        int chunkZ = chunk.getLocation().getZ();

        int chunkIndex = ((chunkX - lowChunkX) * depth) + (chunkZ - lowChunkZ);
        chunkMask.set(chunkIndex, true);
        chunksToWrite[chunkIndex] = chunk;
      }
    });

    // Compress & write chunk data in order of appearance in the chunk mask.
    ByteArrayOutputStream chunkBytesOut = new ByteArrayOutputStream();
    OozeDataOutputStream chunkDataOut = new OozeDataOutputStream(chunkBytesOut);
    for (Chunk chunk : chunksToWrite) {
      if (chunk != null) {
        chunk.serialize(chunkDataOut);
      }
    }

    // Write magic numbers & format version.
    writeHeader();

    // Write world size & location.
    writeByte(width);
    writeByte(depth);
    writeShort(lowChunkX);
    writeShort(lowChunkZ);

    // Write non-empty chunks.
    writeBitSet(chunkMask, chunksToWrite.length);
    writeCompressed(chunkBytesOut.toByteArray());

    // Write NBT extras (entities, block entities, and custom data).
    writeOptionalNBT(!blockEntities.isEmpty(), "BlockEntities", blockEntities);
    writeOptionalNBT(!entities.isEmpty(), "Entities", entities);
    writeOptionalNBT(!customStorage.isEmpty(), "Custom", customStorage);
  }

  /**
   * Writes a {@code chunk} of blocks to the underlying stream.
   * <p><br>
   * <ul>
   *   <li>The first thing written is the chunk's {@link Chunk#getDataVersion() data version},
   *   encoded as a {@link #writeVarInt(int) VarInt}.</li>
   *
   *   <li>Then, the chunk's height and lowest y-coordinate are written in that order as VarInts.
   *   Both are measured in 16-block units.</li>
   *
   *   <li>Following is a {@link #writeBitSet(BitSet, int) BitSet} indicating which 16x16x16 volumes
   *   in the chunk are empty ({@code 1} for non-empty).
   *   <ul>
   *     <li>Volumes in the BitSet are indexed absolutely, so the lowest volume is index {@code 0}
   *     and so on.</li>
   *
   *     <li><strong>If the BitSet is empty (entirely {@code 0}), then no more data follows for the
   *     chunk.</strong></li>
   *   </ul>
   *   </li>
   *
   *   <li>If the chunk has any non-empty volumes, then the chunk's {@link
   *   #writePalette(BlockPalette) palette} is written.</li>
   *
   *   <li>In order of their appearance in the BitSet, each non-empty volume's {@link
   *   PalettedVolume#getStorage() storage container} is is written in the {@link BitCompactIntArray
   *   bit-compact format}.</li>
   * </ul>
   * <p>
   * If the chunk's implementation is custom, {@link OozeChunk#serialize(OozeDataOutputStream)
   * serialize(...)} is called on the chunk, with the current stream as its parameter. Therefore,
   * calling this method from the chunk's serialize(...) method is recursive, and will result in a
   * {@link StackOverflowError}.
   *
   * @throws IOException If the chunk could not be written to the stream.
   */
  public void writeChunk(Chunk chunk) throws IOException {
    if (!(chunk instanceof OozeChunk)) {
      chunk.serialize(this);
      return;
    }
    OozeChunk oozeChunk = (OozeChunk) chunk;

    // Shortcut for completely empty chunks.
    if (chunk.isEmpty()) {
      out.write(0); // Chunk height; 0 = no sections.
      out.write(0); // Lowest section altitude; 0 = default.
      out.write(0); // Section bitmask (nonEmptySections); 0 = all sections empty.
      return;
    }

    int chunkHeight = oozeChunk.getHeight() / 16;
    int minSectionAltitude = oozeChunk.getMinY() / 16;

    BitSet nonEmptySections = new BitSet(chunkHeight);
    List<BitCompactIntArray> sectionsToWrite = new ArrayList<>();

    // Determine which sections are non-empty.
    for (int i = 0; i < chunkHeight; i++) {
      PalettedVolume section = oozeChunk.getSection(i - minSectionAltitude);

      if (section != null && section.isNotEmpty()) {
        // Mark the section as non-air.
        nonEmptySections.set(i);

        BitCompactIntArray storage = BitCompactIntArray.fromIntArray(section.getStorage());
        if (section.getPalette() != oozeChunk.getPalette()) {
          // Merge the section's palette into the chunk's (if it wasn't already).
          oozeChunk.getPalette().addAll(section.getPalette()).upgrade(storage);
        }
        sectionsToWrite.add(storage);
      }
    }

    writeVarInt(oozeChunk.getDataVersion());

    // Write chunk dimensions & section mask.
    writeVarInt(chunkHeight);
    writeVarInt(minSectionAltitude);
    writeBitSet(nonEmptySections, chunkHeight);

    // Only write palette & blocks if there is at least 1 non-empty section.
    if (!nonEmptySections.isEmpty()) {
      writePalette(oozeChunk.getPalette());

      for (BitCompactIntArray storage : sectionsToWrite) {
        write(storage);
      }
    }
  }
}
