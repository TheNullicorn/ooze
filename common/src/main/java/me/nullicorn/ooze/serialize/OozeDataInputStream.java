package me.nullicorn.ooze.serialize;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import me.nullicorn.nedit.NBTReader;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.ooze.ResourceLocation;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.world.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * @author Nullicorn
 */
public class OozeDataInputStream extends DataInputStream {

  private int formatVersion = -1;

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
   * bits. The number of bytes read is equal to {@code Math.ceil(length / (double) Byte.SIZE)}.
   *
   * @param length The length of the bit set, in bits.
   * @throws IOException If the stream cannot be read.
   */
  public BitSet readBitSet(int length) throws IOException {
    return BitSet.valueOf(readBytes((int) Math.ceil(length / (double) Byte.SIZE)));
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
    for (int i = 0; i < entryCount; i++) {
      int length = read();
      boolean hasProperties = ((length & 1) == 0);
      length >>>= 1;

      // Read the name of the block.
      String fullName = new String(readBytes(length));
      ResourceLocation name;
      try {
        name = ResourceLocation.fromString(fullName);
      } catch (IllegalArgumentException e) {
        throw new IOException("Invalid state name in block palette: " + fullName, e);
      }

      // Read the block's properties (if it has any).
      NBTCompound properties = hasProperties
          ? NBTReader.read(this, true, true)
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
      throw new IOException("BitSet ended unexpectedly");
    }
    return bytes;
  }

  /**
   * Reads the next boolean from the stream, followed by an NBT compound if the boolean is true.
   *
   * @return The deserialized NBT compound if one was read, or {@code null} if the initial boolean
   * was {@code false}.
   * @throws IOException If the stream cannt be read, or if the NBT data could not be deserialized.
   */
  @Nullable
  public NBTCompound readOptionalNBT() throws IOException {
    boolean hasData = readBoolean();

    if (hasData) {
      return NBTReader.read(new ByteArrayInputStream(readCompressed()), true, true);
    }
    return null;
  }

  /**
   * Reads a Zstd-compressed section of data from the stream, preceded by two {@link #readVarInt()
   * VarInts} indicating the length of the compressed data.
   *
   * @return The decompressed data in the section.
   * @throws IOException If the stream could not be read, or if the data could not be decompressed.
   */
  public byte[] readCompressed() throws IOException {
    int compressedLength = readVarInt();
    int uncompressedLength = readVarInt();
    return Zstd.decompress(readBytes(compressedLength), uncompressedLength);
  }
}
