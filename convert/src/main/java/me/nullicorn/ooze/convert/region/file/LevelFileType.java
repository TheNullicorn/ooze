package me.nullicorn.ooze.convert.region.file;

import me.nullicorn.ooze.Location2D;

/**
 * Types of files used to store Minecraft levels.
 *
 * @author Nullicorn
 */
public enum LevelFileType {
  /**
   * The legacy format used by Minecraft to store 32x32 chunk areas.
   */
  REGION("r.%s.%s.mcr"),

  /**
   * The current format used by Minecraft to store 32x32 chunk areas.
   */
  ANVIL("r.%s.%s.mca"),

  /**
   * Data for an oversized chunk that was too large to fit in its respective region file.
   */
  CHUNK("c.%s.%s.mcc");

  private final String fileFormat;

  LevelFileType(String fileFormat) {
    this.fileFormat = fileFormat;
  }

  /**
   * @return The file name used for a chunk/region at the given {@code location}.
   */
  public String getFileName(Location2D location) {
    return getFileName(location.getX(), location.getZ());
  }

  /**
   * @return The file name used for a chunk/region at the given X and Z coordinates.
   */
  public String getFileName(int x, int z) {
    return String.format(fileFormat, x, z);
  }
}
