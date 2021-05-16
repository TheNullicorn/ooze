package me.nullicorn.ooze.convert.region;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.InflaterInputStream;
import lombok.Getter;
import me.nullicorn.nedit.NBTReader;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.ooze.Location2D;
import org.jetbrains.annotations.Nullable;

/**
 * A file containing chunk data in Minecraft's region/anvil format.
 *
 * @author Nullicorn
 */
public class RegionFile {

  // Region files are divided into 4KiB sectors
  private static final int SECTOR_LENGTH = 4096;

  @Getter
  private final File   source;
  private       byte[] contents;

  public RegionFile(File source) {
    this.source = source;
  }

  /**
   * Same as {@link #readChunkData(int, int)}, but with {@code chunkX} and {@code chunkZ} provided
   * via a {@link Location2D}.
   *
   * @see #readChunkData(int, int)
   */
  @Nullable
  public NBTCompound readChunkData(Location2D chunkLocation) throws IOException {
    return readChunkData(chunkLocation.getX(), chunkLocation.getZ());
  }

  /**
   * Reads & serializes the data for a chunk in the region.
   *
   * @param chunkX The x-coordinate of the desired chunk.
   * @param chunkZ The z-coordinate of the desired chunk.
   * @return The serialized chunk, or null if it could not be read.
   * @throws IOException If the region file or any part of it could not be read or serialized.
   */
  @Nullable
  public NBTCompound readChunkData(int chunkX, int chunkZ) throws IOException {
    if (contents == null) {
      reload();
    }

    int chunkXOffset = chunkX % 32;
    int chunkZOffset = chunkZ % 32;
    if (chunkXOffset < 0) {
      chunkXOffset += 32;
    }
    if (chunkZOffset < 0) {
      chunkZOffset += 32;
    }

    // Get chunk info from location table.
    int index = (chunkXOffset + chunkZOffset * 32) * 4;
    int sectorOffset = ((contents[index] & 0xFF) << 16)
                       | ((contents[index + 1] & 0xFF) << 8)
                       | (contents[index + 2] & 0xFF);
    int sectorCount = contents[index + 3] & 0xFF;

    if (sectorCount == 0) {
      // Chunk has no data.
      return null;
    }

    int chunkStart = SECTOR_LENGTH * sectorOffset;
    int chunkLength = ((contents[chunkStart] & 0xFF) << 24)
                      | ((contents[chunkStart + 1] & 0xFF) << 16)
                      | ((contents[chunkStart + 2] & 0xFF) << 8)
                      | (contents[chunkStart + 3] & 0xFF);
    int compressionType = contents[chunkStart + 4] & 0xFF;

    if (chunkLength < 1) {
      // Shouldn't happen, but just to be safe.
      return null;
    }

    boolean isExternal = (compressionType & 0x80) != 0;
    compressionType &= 0x7F;

    if (isExternal) {
      return readExternalChunkData(chunkX, chunkZ, compressionType);
    } else if (chunkLength < 2) {
      // Also shouldn't happen (if a chunk isn't external, data __should__ be present).
      return null;
    }

    return deserializeChunkData(contents, chunkStart + 5, chunkLength - 1, compressionType);
  }

  /**
   * Reloads the contents of the region file from disk.
   *
   * @throws IOException If the file could not be found or read.
   */
  public void reload() throws IOException {
    if (!source.isFile()) {
      throw new FileNotFoundException("Region " + source + " is not a file");
    }

    contents = Files.readAllBytes(source.toPath());
    int contentLength = contents.length;
    if (contentLength % SECTOR_LENGTH != 0) {
      contents = null;
      throw new IOException("Region file " + source +
                            " is not a valid size (" + contentLength + " bytes)");
    }
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
    return NBTReader.read(chunkStream);
  }
}
