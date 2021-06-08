package me.nullicorn.ooze.convert.region.file;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.zip.InflaterInputStream;
import lombok.Getter;
import me.nullicorn.nedit.NBTReader;
import me.nullicorn.nedit.type.NBTCompound;
import org.jetbrains.annotations.Nullable;

/**
 * A file containing chunk data in Minecraft's region/anvil format.
 *
 * @author Nullicorn
 */
public class RegionFile implements ChunkSource {

  private static final int LOCATION_TABLE_LENGTH = 4096;

  // Region files are divided into 4KiB sectors
  private static final int SECTOR_LENGTH = 4096;

  @Getter
  private final File source;

  @Nullable
  private FileChannel channel;

  @Nullable
  private byte[] locationTable;

  public RegionFile(File source) {
    this.source = source;
  }

  @Nullable
  public NBTCompound loadChunk(int chunkX, int chunkZ) throws IOException {
    open();

    if (channel == null || locationTable == null) {
      throw new IllegalStateException("Cannot read chunks while file is closed.");
    }

    // Get chunk info from location table.
    int index = (chunkX & 31 | (chunkZ & 31) << 5) << 2;
    int sectorOffset = ((locationTable[index] & 0xFF) << 16)
                       | ((locationTable[index + 1] & 0xFF) << 8)
                       | (locationTable[index + 2] & 0xFF);
    int sectorCount = locationTable[index + 3] & 0xFF;

    if (sectorCount == 0) {
      // Chunk has no data.
      return null;
    }

    ByteBuffer chunkDataBuf = ByteBuffer.allocate(sectorCount * SECTOR_LENGTH);
    channel.position(sectorOffset * (long) SECTOR_LENGTH);
    channel.read(chunkDataBuf);
    byte[] chunkData = chunkDataBuf.array();

    int chunkLength = ((chunkData[0] & 0xFF) << 24)
                      | ((chunkData[1] & 0xFF) << 16)
                      | ((chunkData[2] & 0xFF) << 8)
                      | (chunkData[3] & 0xFF);
    int compressionType = chunkData[4] & 0xFF;

    if (chunkLength < 1) {
      // Shouldn't happen, but just to be safe.
      return null;
    }

    boolean isExternal = (compressionType & 0x80) != 0;
    compressionType &= 0x7F;

    if (isExternal) {
      return readExternalChunkData(chunkX, chunkZ, compressionType);
    } else if (chunkLength < 2) {
      // Also shouldn't happen; if a chunk isn't external, data should be present.
      return null;
    }

    return deserializeChunkData(chunkData, 5, chunkLength - 1, compressionType);
  }

  /**
   * Opens the contents of the region file, reading enough information so that future chunks can be
   * properly read from it.
   *
   * @throws IOException If the file could not be found, opened, or read.
   */
  public void open() throws IOException {
    if (channel != null && channel.isOpen()) {
      return;
    }

    if (!source.isFile()) {
      throw new FileNotFoundException("Region " + source + " is not a file");
    }

    ByteBuffer locationsBuf = ByteBuffer.allocate(LOCATION_TABLE_LENGTH);
    channel = FileChannel.open(source.toPath(), StandardOpenOption.READ);
    channel.read(locationsBuf);
    locationTable = locationsBuf.array();
  }

  @Override
  public void close() throws IOException {
    if (channel == null) {
      return;
    }

    channel.close();
    channel = null;
  }

  /**
   * Reads the data for an external/oversized chunk stored alongside this region file.
   *
   * @param chunkX          The x-coordinate of the chunk.
   * @param chunkZ          The z-coordinate of the chunk.
   * @param compressionType The type of compression used to store the chunk; 1, 2, or 3
   * @return The NBT data for the chunk, or null if an external file could not be found for the
   * chunk.
   * @throws IOException If the chunk file could not be read.
   */
  @Nullable
  private NBTCompound readExternalChunkData(int chunkX, int chunkZ, int compressionType)
      throws IOException {
    String fileName = LevelFileType.CHUNK.getFileName(chunkX, chunkZ);

    File chunkFile = new File(source.getParentFile(), fileName);
    if (!chunkFile.isFile()) {
      return null;
    }

    byte[] chunkData = Files.readAllBytes(chunkFile.toPath());
    return deserializeChunkData(chunkData, 0, chunkData.length, compressionType);
  }

  /**
   * Serializes raw chunk data, decompressing if necessary.
   *
   * @param offset          The starting index of the chunk within the <code>data</code> array.
   * @param length          The length of the chunk data in the <code>data</code> array.
   * @param compressionType The type of compression used to store the chunk; 1, 2, or 3
   * @return An NBT compound representing the serialized chunk.
   * @throws IOException If the data could not properly be decompressed or was not valid NBT.
   */
  private NBTCompound deserializeChunkData(byte[] data, int offset, int length, int compressionType)
      throws IOException {
    InputStream chunkStream = new ByteArrayInputStream(data, offset, length);
    if (compressionType < 1 || compressionType > 3) {
      throw new IOException("Unknown chunk compression type: " + compressionType);
    }

    if (compressionType == 2) {
      // Only Zlib needs checked, NBTReader automatically takes care of Gzip.
      chunkStream = new InflaterInputStream(chunkStream);
    }
    return NBTReader.read(chunkStream, true, true);
  }
}
