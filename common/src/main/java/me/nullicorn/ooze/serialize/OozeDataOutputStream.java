package me.nullicorn.ooze.serialize;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import me.nullicorn.nedit.NBTOutputStream;
import me.nullicorn.nedit.NBTWriter;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.nedit.type.TagType;
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

  private final int compressionLevel;

  // Used by `beginCompression()` and `endCompression()` to ensure that compression is done in the
  // correct order by users.
  private boolean isCompressing = false;

  // When compression, the stream's destination (`out`) is replaced with a temporary buffer
  // (ByteArrayOutputStream) that holds the compressed data. When that happens, the stream's actual
  // destination is kept here so that it can be put back into `out` when compression is over. If
  // compression is not in progress, then this is set to `null`.
  private OutputStream tempOut;

  /**
   * Same as {@link OozeDataOutputStream#OozeDataOutputStream(OutputStream, int)}, but {@code
   * compressionLevel} defaults to {@code 3}.
   */
  public OozeDataOutputStream(OutputStream out) {
    this(out, 3);
  }

  /**
   * @param compressionLevel The level of Zstandard compression to be used by {@link
   *                         #beginCompression()} and {@link #endCompression()}, as well as any
   *                         writer methods that depend on compression.
   */
  public OozeDataOutputStream(OutputStream out, int compressionLevel) {
    super(out);

    // Check that compression level is in bounds.
    int minLevel = Zstd.minCompressionLevel();
    int maxLevel = Zstd.maxCompressionLevel();
    if (compressionLevel < minLevel || compressionLevel > maxLevel) {
      throw new IllegalArgumentException("Compression level (" + compressionLevel +
                                         ") must be between " + minLevel + " and " + maxLevel);
    }

    this.compressionLevel = compressionLevel;
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
      write(0);
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

    NBTOutputStream propertyWriter = new NBTOutputStream(out, false);
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
        propertyWriter.writeCompound(state.getProperties());
      }
    }
  }

  /**
   * Tells the stream that any bytes written after a call to this method should be compressed as a
   * single unit. When the compressed section is done being written, {@link #endCompression()} must
   * be called to finalize & flush the compressed data to the underlying stream.
   * <p><br>
   * Notes:
   * <ul>
   *   <li>Compression is done using Zstandard with the level provided at the stream's
   *   construction.</li>
   *   <li>Compression is not recursive. It can either be enabled or disabled at any given time for
   *   a single stream. Attempting to use this method multiple times without calling {@link
   *   #endCompression()} will cause an {@link IllegalStateException} to be thrown.</li>
   * </ul>
   *
   * @throws IllegalStateException If the stream was already in compression mode. This happens if
   *                               the method is used multiple times without matching calls to
   *                               {@link #endCompression()}.
   * @see #endCompression()
   */
  // Suppressed because IOException is included for consistency, as well as to future-proof the API.
  @SuppressWarnings({"RedundantThrows", "java:S1130"})
  public void beginCompression() throws IOException {
    if (isCompressing) {
      throw new IllegalStateException("Compression is already in progress");
    }

    isCompressing = true;
    tempOut = out;
    out = new ByteArrayOutputStream();
  }

  /**
   * Tells the stream that a segment of compressed data is finished, causing that data to be flushed
   * to the stream. See {@link #beginCompression()} for usage and compression details.
   *
   * @throws IOException           If the data could not be compressed or written to the stream.
   * @throws IllegalStateException If a compressed section had not already been started.
   * @see #beginCompression()
   */
  public void endCompression() throws IOException {
    if (!isCompressing || !(out instanceof ByteArrayOutputStream)) {
      throw new IllegalStateException("Attempted to end compression without beginning");
    }

    byte[] uncompressed = ((ByteArrayOutputStream) out).toByteArray();
    byte[] compressed;
    try {
      compressed = Zstd.compress(uncompressed, compressionLevel);
    } catch (ZstdException e) {
      throw new IOException("Failed to compress buffer", e);
    }

    isCompressing = false;
    out = tempOut;
    tempOut = null;

    writeVarInt(uncompressed.length);
    writeVarInt(compressed.length);
    write(compressed);
  }

  /**
   * Writes a list of NBT compounds to the stream.
   * <p><br>
   * First, a {@link #writeVarInt(int) VarInt} is written to the stream indicating the array's
   * length. If the list is empty, nothing more is written. Otherwise, inside a {@link
   * #beginCompression() compressed section}, each compound in the list is serialized. The compounds
   * themselves are written directly without a name, and are delimited only by their respective
   * {@link TagType#END END} tags. After all compounds are written, the compressed section is {@link
   * #endCompression() closed}.
   *
   * @param list A list of {@link NBTCompound compounds} to write to the stream. May be empty, but
   *             not null.
   * @throws IOException              If the elements in the list could not be serialized, if the
   *                                  compression failed, or if the compressed list could not be
   *                                  written to the stream.
   * @throws IllegalArgumentException If the list is non-empty but its {@link NBTList#getContentType()
   *                                  contents} are not {@link TagType#COMPOUND COMPOUND}s.
   */
  public void writeList(NBTList list) throws IOException {
    if (!list.isEmpty() && list.getContentType() != TagType.COMPOUND) {
      throw new IllegalArgumentException("Can only write lists of compounds");
    }

    // Write the list's size.
    writeVarInt(list.size());

    if (!list.isEmpty()) {
      beginCompression();

      NBTOutputStream nbtOut = new NBTOutputStream(out, false);
      for (Object element : list) {
        nbtOut.writeCompound((NBTCompound) element);
      }

      endCompression();
    }
  }

  /**
   * Writes an entire {@code level} to the underlying stream.
   * <p>
   * // TODO: 6/10/21 Document level encoding.
   *
   * @throws IOException If any part of the level could not be written to the stream.
   */
  public void writeLevel(BoundedLevel<OozeChunk> level) throws IOException {
    int width = level.getWidth();
    int depth = level.getDepth();
    int minChunkX = level.getLowestChunkPos().getX();
    int minChunkZ = level.getLowestChunkPos().getZ();

    NBTList blockEntities = level.getBlockEntities();
    NBTList entities = level.getEntities();
    NBTCompound customStorage = level.getCustomStorage();

    // Generate the chunk mask.
    OozeChunk[] chunksToWrite = new OozeChunk[width * depth];
    BitSet chunkMask = new BitSet(chunksToWrite.length);
    level.getStoredChunks().forEach(chunk -> {
      if (!chunk.isEmpty()) {
        int chunkX = chunk.getLocation().getX();
        int chunkZ = chunk.getLocation().getZ();

        int chunkIndex = ((chunkX - minChunkX) * depth) + (chunkZ - minChunkZ);
        chunkMask.set(chunkIndex, true);
        chunksToWrite[chunkIndex] = chunk;
      }
    });

    // Write magic numbers & format version.
    writeHeader();

    // Write world size & location.
    writeByte(width);
    writeByte(depth);
    writeShort(minChunkX);
    writeShort(minChunkZ);

    // Write chunk mask & compressed chunk data.
    writeBitSet(chunkMask, chunksToWrite.length);
    beginCompression();
    for (OozeChunk chunk : chunksToWrite) {
      if (chunk != null) {
        writeChunk(chunk);
      }
    }
    endCompression();

    // Write NBT entities & tile entities (compressed separately).
    writeList(blockEntities);
    writeList(entities);

    // Optionally write custom data (compressed).
    boolean hasCustomStorage = !customStorage.isEmpty();
    writeBoolean(hasCustomStorage);
    if (hasCustomStorage) {
      beginCompression();
      NBTWriter.write(customStorage, out, false);
      endCompression();
    }
  }

  /**
   * Writes a {@code chunk} of blocks to the underlying stream.
   * <p><br>
   * <ul>
   *   <li>The first thing written is the chunk's {@link Chunk#getDataVersion() data version},
   *   encoded as a {@link #writeVarInt(int) VarInt}.</li>
   *
   *   <li>Then, the chunk's height and lowest Y coordinate are written in that order as VarInts.
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
  public void writeChunk(OozeChunk chunk) throws IOException {
    // Shortcut for completely empty chunks.
    if (chunk.isEmpty()) {
      write(0); // Chunk height; 0 = no sections.
      write(0); // Lowest section altitude; 0 = default.
      write(0); // Section bitmask (nonEmptySections); 0 = all sections empty.
      return;
    }

    int chunkHeight = chunk.getHeight() / 16;
    int minSectionAltitude = chunk.getMinY() / 16;

    BitSet nonEmptySections = new BitSet(chunkHeight);
    List<BitCompactIntArray> sectionsToWrite = new ArrayList<>();

    // Determine which sections are non-empty.
    for (int i = 0; i < chunkHeight; i++) {
      PalettedVolume section = chunk.getSection(i + minSectionAltitude);

      if (section != null && section.isNotEmpty()) {
        // Mark the section as non-air.
        nonEmptySections.set(i);

        BitCompactIntArray storage = BitCompactIntArray.fromIntArray(section.getStorage());
        if (section.getPalette() != chunk.getPalette()) {
          // Merge the section's palette into the chunk's (if it wasn't already).
          chunk.getPalette().addAll(section.getPalette()).upgrade(storage);
        }
        sectionsToWrite.add(storage);
      }
    }

    writeVarInt(chunk.getDataVersion());

    // Write chunk dimensions & section mask.
    writeVarInt(chunkHeight);
    writeVarInt(minSectionAltitude);
    writeBitSet(nonEmptySections, chunkHeight);

    // Only write palette & blocks if there is at least 1 non-empty section.
    if (!nonEmptySections.isEmpty()) {
      writePalette(chunk.getPalette());

      for (BitCompactIntArray storage : sectionsToWrite) {
        write(storage);
      }
    }
  }
}
