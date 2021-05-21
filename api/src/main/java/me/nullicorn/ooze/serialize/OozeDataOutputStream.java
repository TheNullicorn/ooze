package me.nullicorn.ooze.serialize;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.BitSet;
import me.nullicorn.nedit.NBTWriter;
import me.nullicorn.nedit.type.NBTCompound;

/**
 * @author Nullicorn
 */
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
    if (value == 0) {
      write(value);
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
   * Writes a BitSet to the underlying stream, padding it to {@code byteCount} if it is shorter than
   * that many bytes, and trimming it if it is longer.
   *
   * @throws IOException If the bytes could not be written.
   */
  public void writeBitSet(BitSet value, int byteCount) throws IOException {
    byte[] bytes = value.toByteArray();
    if (bytes.length != byteCount) {
      bytes = Arrays.copyOf(bytes, byteCount);
    }
    write(bytes);
  }

  /**
   * Writes an uncompressed NBT compound to the underlying output stream. The NBT data is prefixed
   * by its length in bytes as a LEB128 integer.
   *
   * @param wrap Whether or not the provided compound should be wrapped in an unnamed compound
   *             before serializing.
   * @throws IOException If the compound cannot be serialized or written.
   */
  public void writeNBT(NBTCompound compound, boolean wrap) throws IOException {
    if (wrap) {
      NBTCompound wrapper = new NBTCompound();
      wrapper.put("", compound);
      compound = wrapper;
    }

    ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    NBTWriter.write(compound, bytesOut, false);

    byte[] nbtBytes = bytesOut.toByteArray();
    writeVarInt(nbtBytes.length);
    write(nbtBytes);
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
      NBTCompound wrapper = new NBTCompound();
      NBTCompound root = new NBTCompound();

      wrapper.put("", root);
      root.put(key, value);

      // Serialize & compress the contents of the wrapper compound.
      ByteArrayOutputStream nbtOut = new ByteArrayOutputStream();
      NBTWriter.write(wrapper, nbtOut, false);
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
}
