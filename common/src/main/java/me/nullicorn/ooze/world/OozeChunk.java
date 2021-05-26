package me.nullicorn.ooze.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import lombok.Getter;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.nedit.type.TagType;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.serialize.OozeDataOutputStream;
import me.nullicorn.ooze.storage.BitCompactIntArray;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.storage.PalettedVolume;
import org.jetbrains.annotations.Nullable;

/**
 * @author Nullicorn
 */
public class OozeChunk implements Chunk {

  static final         int WIDTH              = 16;
  static final         int DEPTH              = 16;
  static final         int SECTION_HEIGHT     = 16;
  public static final  int SECTIONS_PER_CHUNK = 16;
  private static final int HEIGHT             = SECTION_HEIGHT * SECTIONS_PER_CHUNK;

  @Getter
  private final Location2D location;

  @Getter
  private final int dataVersion;

  private final BlockPalette palette;

  // Individual sections in this array may be null if they are empty, though empty sections can also
  // be present.
  private final BitCompactIntArray[] sections;

  /*
   * Simple cache for checking if a section is entirely air blocks or not. `isSectionChecked[]`
   * indicates whether or not each section's `isSectionEmpty[]` value is valid. If not,
   * `isSectionEmpty[]` should be recalculated for that section so it can be marked as checked. Null
   * sections should automatically be marked as both empty and checked.
   */
  private final boolean[] isSectionEmpty;
  private final boolean[] isSectionChecked;

  /**
   * Serialized data for any entities in the chunk.
   */
  @Getter
  private final NBTList entities;

  /**
   * Serialized data for any block entities in the chunk.
   */
  @Getter
  private final NBTList blockEntities;

  public OozeChunk(Location2D location, int dataVersion) {
    this.location = location;
    this.dataVersion = dataVersion;
    palette = new BlockPalette();
    entities = new NBTList(TagType.COMPOUND);
    blockEntities = new NBTList(TagType.COMPOUND);

    sections = new BitCompactIntArray[SECTIONS_PER_CHUNK];
    isSectionEmpty = new boolean[sections.length];
    isSectionChecked = new boolean[sections.length];

    // All sections start out null, so we know that they are all empty.
    Arrays.fill(isSectionEmpty, true);
    Arrays.fill(isSectionChecked, true);
  }

  /**
   * Sets the block data for a 16x16x16 region of the chunk.
   *
   * @param altitude How high the section's base is from the bottom of the chunk, in units of 16
   *                 blocks.
   * @throws IndexOutOfBoundsException If the section's altitude is out of the chunk's boundaries.
   */
  public void setSection(int altitude, @Nullable PalettedVolume section) {
    if (altitude < 0 || altitude >= SECTIONS_PER_CHUNK) {
      throw new IndexOutOfBoundsException("Cannot store chunk section at altitude " + altitude);
    }

    if (section == null) {
      // Null sections are marked as empty.
      sections[altitude] = null;
      isSectionEmpty[altitude] = true;
      isSectionChecked[altitude] = true;
    } else {
      // Upgrade the section's storage to use the entire chunk's palette.
      BitCompactIntArray storage = BitCompactIntArray.fromIntArray(section.getStorage());
      palette.addAll(section.getPalette()).upgrade(storage);

      sections[altitude] = storage;
      isSectionChecked[altitude] = false; // Tells us to recalculate isEmpty later.
    }
  }

  @Override
  public int getWidth() {
    return WIDTH;
  }

  @Override
  public int getHeight() {
    return HEIGHT;
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
    return 0;
  }

  @Override
  public int getMinZ() {
    return 0;
  }

  @Override
  public BlockState getBlockAt(int x, int y, int z) {
    if (!isInBounds(x, y, z)) {
      throw new IllegalArgumentException("Chunk coordinates out of bound: (" +
                                         x + ", " + y + ", " + z + ")");
    }

    BitCompactIntArray section = sections[y / 16];
    if (section == null) {
      return BlockState.DEFAULT;
    }
    int stateId = section.get(getBlockIndex(x, y % 16, z));
    return palette.getState(stateId);
  }

  @Override
  public boolean isEmpty() {
    for (int i = 0; i < sections.length; i++) {
      if (!isSectionEmpty(i)) {
        return false;
      }
    }
    return true;
  }

  private boolean isSectionEmpty(int altitude) {
    // Check if isEmpty is cached for the section.
    if (isSectionChecked[altitude]) {
      if (!isSectionEmpty[altitude]) {
        return false;
      }
    } else {
      // Recalculate isEmpty for the section.
      BitCompactIntArray section = sections[altitude];
      for (int blockIndex = 0; blockIndex < section.size(); blockIndex++) {
        // Check if any block is not air.
        int stateId = section.get(blockIndex);
        if (!palette.getState(stateId).isAir()) {
          isSectionEmpty[altitude] = false;
          isSectionChecked[altitude] = true;
          return false;
        }
      }
    }

    // The section is empty.
    isSectionEmpty[altitude] = true;
    isSectionChecked[altitude] = true;
    return true;
  }

  @Override
  public void serialize(OozeDataOutputStream out) throws IOException {
    out.writeVarInt(dataVersion);

    // TODO: Write biome list.

    // Determine which sections are empty.
    BitSet nonEmptySections = new BitSet(sections.length);
    List<BitCompactIntArray> sectionsToWrite = new ArrayList<>();
    for (int i = 0; i < sections.length; i++) {
      if (!isSectionEmpty(i)) {
        nonEmptySections.set(i);
        sectionsToWrite.add(sections[i]);
      }
    }
    out.writeBitSet(nonEmptySections, 2);

    // Only write palette & blocks if there is at least 1 non-empty section.
    if (!nonEmptySections.isEmpty()) {
      out.writePalette(palette);
      for (BitCompactIntArray storage : sectionsToWrite) {
        out.write(storage);
      }
    }
  }

  /**
   * @return The index in the storage container where a block at the provided coordinates would be
   * stored.
   */
  private int getBlockIndex(int x, int y, int z) {
    int height = getHeight();
    return (y * height * height) + (z * getDepth()) + x;
  }
}
