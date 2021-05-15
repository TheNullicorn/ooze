package me.nullicorn.ooze.serialize;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import me.nullicorn.nedit.NBTWriter;
import me.nullicorn.nedit.type.NBTCompound;

/**
 * @author Nullicorn
 */
public class OozeDataOutputStream extends DataOutputStream {

  private static final int MAGIC_NUMBER   = 0x610BB10B;
  private static final int FORMAT_VERSION = 0;

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
    writeByte(getFormatVersion());
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
   * Same as {@link #writeCompressed(byte[], int)}, but compression {@code level} defaults to
   * {@literal 3}.
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

    writeInt(compressed.length);
    writeInt(data.length);
    write(compressed);
  }
}
