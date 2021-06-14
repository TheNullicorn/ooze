package me.nullicorn.ooze.serialize;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import me.nullicorn.nedit.NBTInputStream;
import me.nullicorn.nedit.NBTReader;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.nedit.type.TagType;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.ResourceLocation;
import me.nullicorn.ooze.storage.BitCompactIntArray;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.storage.IntArray;
import me.nullicorn.ooze.world.BlockState;
import me.nullicorn.ooze.world.BoundedLevel;
import me.nullicorn.ooze.world.OozeChunk;
import me.nullicorn.ooze.world.OozeChunkSection;
import me.nullicorn.ooze.world.OozeLevel;

/**
 * @author Nullicorn
 */
// TODO: 6/9/21 Add biome support.
public class OozeDataInputStream extends DataInputStream {

  private int formatVersion = -1;

  private boolean     isDecompressing = false;
  private InputStream actualIn;

  public OozeDataInputStream(InputStream in) {
    super(in);
  }

  /**
   * @return The Ooze format version used by the data in the stream, or {@code -1} if a valid header
   * has not been read.
   * @throws IllegalStateException If {@link #checkHeader()} has not returned true yet.
   * @see #checkHeader()
   */
  public int getFormatVersion() {
    if (formatVersion == -1) {
      throw new IllegalStateException("Format version has not been read");
    }
    return formatVersion;
  }

  /**
   * Reads an integer (4 bytes) followed by a {@link #readVarInt() VarInt} to determine whether or
   * not the data is in a version of the Ooze format that this stream can understand. If {@code
   * true} is returned, the value returned by {@link #getFormatVersion()} will be initialized.
   */
  public boolean checkHeader() {
    try {
      if (readInt() == OozeDataOutputStream.MAGIC_NUMBER) {
        int version = readVarInt();
        if (version >= 0 && version <= OozeDataOutputStream.FORMAT_VERSION) {
          formatVersion = version;
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Reads the next LEB128 VarInt from the stream.
   *
   * @throws IOException If the stream cannot be read.
   */
  public int readVarInt() throws IOException {
    int value = 0;

    int bytesRead = 0;
    int lastByte;
    do {
      lastByte = read();
      value |= ((lastByte & 0x7F) << (7 * bytesRead));
      bytesRead++;

      if (bytesRead > 5) {
        throw new IOException("VarInt too big");
      }
    } while ((lastByte & 0x80) != 0);

    return value;
  }

  /**
   * Reads however many bytes from the stream are needed to create a BitSet with {@code length}
   * bits.
   * <p><br>
   * The actual number of bytes read is equal to
   * <pre>(int) Math.ceil((float) length / Byte.SIZE)</pre>
   *
   * @param length The length of the bit set, in bits.
   * @throws IOException If the stream cannot be read.
   */
  public BitSet readBitSet(int length) throws IOException {
    int lengthInBytes = (int) Math.ceil((float) length / Byte.SIZE);
    return BitSet.valueOf(readBytes(lengthInBytes));
  }

  /**
   * Reads however many bytes are needed to read a full palette of blocks from the stream. The
   * encoding used is described in {@link OozeDataOutputStream#writePalette(BlockPalette)}.
   *
   * @throws IOException If the stream cannot be read.
   * @see OozeDataOutputStream#writePalette(BlockPalette)
   */
  public BlockPalette readPalette() throws IOException {
    int entryCount = readVarInt();
    if (entryCount == 0) {
      // Return an empty palette if there are no entries.
      return new BlockPalette();
    }

    BlockPalette palette = null;
    NBTInputStream propertyReader = new NBTInputStream(in, true, true);
    for (int i = 0; i < entryCount; i++) {
      int length = read();
      boolean hasProperties = ((length & 1) != 0);
      length >>>= 1;

      // Read the name of the block.
      String fullName = new String(readBytes(length));
      ResourceLocation name = ResourceLocation.fromString(fullName);
      if (name == null) {
        throw new IOException("Invalid state name in block palette: " + fullName);
      }

      // Read the block's properties (if it has any).
      NBTCompound properties = hasProperties
          ? propertyReader.readCompound()
          : null;

      // Add the state to the palette.
      BlockState state = new BlockState(name, properties);
      if (palette == null) {
        // The first entry is the default for the palette.
        palette = new BlockPalette(state);
      } else {
        palette.addState(state);
      }
    }

    return palette;
  }

  /**
   * Reads the next {@code length} bytes from the stream.
   *
   * @param length The number of bytes to read.
   * @throws IOException If the full number of bytes cannot be read.
   */
  public byte[] readBytes(int length) throws IOException {
    byte[] bytes = new byte[length];
    if (read(bytes) < bytes.length) {
      throw new IOException("Byte array ended unexpectedly");
    }
    return bytes;
  }

  public void beginDecompression() throws IOException {
    if (isDecompressing) {
      throw new IllegalStateException("Already decompressing");
    }

    int uncompressedLength = readVarInt();
    int compressedLength = readVarInt();

    byte[] compressed = new byte[compressedLength];
    readFully(compressed);
    byte[] decompressed = Zstd.decompress(compressed, uncompressedLength);

    isDecompressing = true;
    actualIn = in;
    in = new ByteArrayInputStream(decompressed);
  }

  public void endDecompression() {
    if (!isDecompressing) {
      throw new IllegalStateException("Attempted to end decompression without beginning");
    }

    isDecompressing = false;
    in = actualIn;
    actualIn = null;
  }

  public NBTList readList() throws IOException {
    // Read & validate the list's size.
    int size = readVarInt();
    if (size < 0) {
      throw new IOException(new NegativeArraySizeException("List size is negative"));
    }

    // Read & insert each compound into the list.
    NBTList list = new NBTList(TagType.COMPOUND);
    if (size > 0) {
      beginDecompression(); // List elements are all compressed together.
      NBTInputStream elementReader = new NBTInputStream(in, true, true);
      for (int i = 0; i < size; i++) {
        list.add(elementReader.readCompound());
      }
      endDecompression();
    }
    return list;
  }

  /**
   * Reads however many bytes are needed to read a full level from the stream. The encoding used is
   * described in {@link OozeDataOutputStream#writeLevel(BoundedLevel)}.
   *
   * @throws IOException If the stream could not be read.
   * @see OozeDataOutputStream#writeChunk(OozeChunk)
   */
  public BoundedLevel<OozeChunk> readLevel() throws IOException {
    if (!checkHeader()) {
      throw new IOException("Invalid data header");
    }

    OozeLevel level = new OozeLevel();

    int width = readUnsignedByte();
    int depth = readUnsignedByte();
    int lowChunkX = readShort();
    int lowChunkZ = readShort();

    BitSet chunkMask = readBitSet(width * depth);

    // Read & decompress all chunk data.
    beginDecompression();
    for (int absChunkX = 0; absChunkX < width; absChunkX++) {
      for (int absChunkZ = 0; absChunkZ < depth; absChunkZ++) {
        // Only store chunks marked as non-empty.
        if (chunkMask.get((absChunkX * depth) + absChunkZ)) {
          level.storeChunk(readChunk(absChunkX + lowChunkX, absChunkZ + lowChunkZ));
        }
      }
    }
    endDecompression();

    level.getBlockEntities().addAll(readList());
    level.getEntities().addAll(readList());

    boolean hasCustomStorage = readBoolean();
    if (hasCustomStorage) {
      // Read & decompress custom storage data.
      beginDecompression();
      level.getCustomStorage().putAll(NBTReader.read(in, true, true));
      endDecompression();
    }

    return level;
  }

  /**
   * Reads however many bytes are needed to read a full chunk of blocks from the stream. The
   * encoding used is described in {@link OozeDataOutputStream#writeChunk(OozeChunk)}.
   *
   * @throws IOException If the stream could not be read.
   * @see OozeDataOutputStream#writeChunk(OozeChunk)
   */
  public OozeChunk readChunk(int chunkX, int chunkZ) throws IOException {
    int dataVersion = readVarInt();
    int chunkHeight = readVarInt();
    int minSectionAltitude = readVarInt();
    BitSet nonEmptySections = readBitSet(chunkHeight);

    OozeChunk chunk = new OozeChunk(new Location2D(chunkX, chunkZ), dataVersion);
    if (nonEmptySections.isEmpty()) {
      // The chunk is entirely air; return the empty chunk immediately.
      return chunk;
    }

    // Create the chunk and its palette.
    BlockPalette chunkPalette = chunk.getPalette();
    chunkPalette.addAll(readPalette());

    // Read the chunk's sections.
    for (int absAltitude = 0; absAltitude < chunkHeight; absAltitude++) {

      // Only store the section if it isn't empty.
      if (nonEmptySections.get(absAltitude)) {
        // Read the section's block array.
        IntArray storage = BitCompactIntArray.deserialize(this, 4096);

        // Store the section in the chunk.
        int altitude = absAltitude + minSectionAltitude;
        chunk.setSection(altitude, new OozeChunkSection(altitude, chunkPalette, storage));
      }
    }

    return chunk;
  }
}
