package me.nullicorn.ooze.world;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.serialize.OozeDataOutputStream;
import me.nullicorn.ooze.storage.BitCompactIntArray;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.storage.BlockVolume;
import me.nullicorn.ooze.storage.PalettedVolume;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Nullicorn
 */
public class OozeChunk implements Chunk, Iterable<OozeChunkSection> {

  static final int WIDTH          = 16;
  static final int DEPTH          = 16;
  static final int SECTION_HEIGHT = 16;

  /**
   * @return Whether or not the {@code volume}'s dimensions allow for it to be a section in a chunk.
   */
  private static boolean canVolumeBeSection(BlockVolume volume) {
    return volume != null
           && volume.getWidth() == WIDTH
           && volume.getHeight() == DEPTH
           && volume.getDepth() == SECTION_HEIGHT;
  }

  @Getter
  private final Location2D location;

  @Getter
  private final int dataVersion;

  @Getter
  private final BlockPalette palette;

  // Individual sections in this set may be null if they are empty, though empty sections can also
  // be present. Each section should have a unique altitude relative to other sections in the chunk.
  private final Set<OozeChunkSection> sections;

  // The lowest and highest altitudes of any section in the chunk. These are initialized the
  // opposite  way so that when comparing, the first section added should always causes these to
  // change.
  private int lowestSectionAltitude  = Integer.MAX_VALUE;
  private int highestSectionAltitude = Integer.MIN_VALUE;

  public OozeChunk(Location2D location, int dataVersion) {
    this.location = location;
    this.dataVersion = dataVersion;
    palette = new BlockPalette();
    sections = new TreeSet<>(Comparator.comparingInt(OozeChunkSection::getAltitude));
  }

  /**
   * @param altitude The distance between the bottom of the chunk (y=0) and the section's base,
   *                 measured in 16-block units.
   * @return The volume used to store blocks between {@code altitude} and {@code altitude + 1}, or
   * {@code null} if no data exists for that section of blocks.
   */
  @Nullable
  public PalettedVolume getSection(int altitude) {
    for (OozeChunkSection section : sections) {
      if (section.getAltitude() == altitude) {
        return section;
      }
    }
    return null;
  }

  /**
   * Sets the block data for a 16x16x16 region of the chunk.
   *
   * @param altitude The distance between the bottom of the chunk (y=0) and the section's base,
   *                 measured in 16-block units.
   * @param section  The storage container to use for blocks between {@code altitude} and {@code
   *                 altitude + 1}.
   * @throws IllegalArgumentException If the section is null, or if it is not 16x16x16 blocks.
   * @throws IllegalStateException    If the chunk already has block data for that altitude.
   */
  public void setSection(int altitude, PalettedVolume section) {
    if (section == null) {
      throw new IllegalArgumentException("Cannot set chunk section " +
                                         altitude + " to null @ " + location);
    } else if (!canVolumeBeSection(section)) {
      throw new IllegalArgumentException("Invalid dimensions for section " +
                                         altitude + " @ " + location);
    }

    // Ensure no section already exists at that altitude.
    for (OozeChunkSection existingSection : sections) {
      if (existingSection.getAltitude() == altitude) {
        throw new IllegalStateException("Duplicate section " + altitude + " @ " + location);
      }
    }

    BitCompactIntArray storage = BitCompactIntArray.fromIntArray(section.getStorage());

    // Merge the section's palette into the chunk's.
    // If the section is already using the chunk's palette, this is not necessary.
    if (section.getPalette() != palette) {
      palette.addAll(section.getPalette()).upgrade(storage);
    }

    // Insert the section into the chunk.
    sections.add(new OozeChunkSection(altitude, palette, storage));

    // Update the lowest & highest known altitudes accordingly.
    if (altitude < lowestSectionAltitude) {
      lowestSectionAltitude = altitude;
    }
    if (altitude > highestSectionAltitude) {
      highestSectionAltitude = altitude;
    }
  }

  @Override
  public int getWidth() {
    return WIDTH;
  }

  @Override
  public int getHeight() {
    return sections.isEmpty()
        ? 0
        : SECTION_HEIGHT * (highestSectionAltitude - lowestSectionAltitude + 1);
  }

  @Override
  public int getDepth() {
    return DEPTH;
  }

  @Override
  public int getMinX() {
    return 0;
  }

  @Override
  public int getMinY() {
    return sections.isEmpty()
        ? 0
        : lowestSectionAltitude * SECTION_HEIGHT;
  }

  @Override
  public int getMinZ() {
    return 0;
  }

  @Override
  public BlockState getBlockAt(int x, int y, int z) {
    if (x < 0 || z < 0 || x >= 16 || z >= 16) {
      throw new IllegalArgumentException("Chunk coordinates out of bounds: (" +
                                         x + ", " + y + ", " + z + ")");
    }

    PalettedVolume section = getSection(y / 16);
    if (section == null) {
      return BlockState.DEFAULT;
    }
    return section.getBlockAt(x, y % 16, z);
  }

  @Override
  public boolean isEmpty() {
    for (OozeChunkSection section : sections) {
      if (section.isNotEmpty()) {
        // If any section has non-air blocks, the chunk isn't empty either.
        return false;
      }
    }
    return true;
  }

  @NotNull
  @Override
  public Iterator<OozeChunkSection> iterator() {
    return sections.iterator();
  }

  @Override
  public void serialize(OozeDataOutputStream out) throws IOException {
    // This won't cause recursion because writeChunk(...) handles OozeChunks differently. However...
    // TODO: 6/10/21 Serialization could be designed a lot better; this ^ seems dumb.
    out.writeChunk(this);
  }
}
