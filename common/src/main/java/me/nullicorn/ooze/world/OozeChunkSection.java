package me.nullicorn.ooze.world;

import lombok.Getter;
import me.nullicorn.ooze.storage.BitCompactIntArray;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.storage.IntArray;
import me.nullicorn.ooze.storage.PalettedVolume;

/**
 * A 16x16x16 volume that stores some of the blocks of an {@link OozeChunk}.
 *
 * @author Nullicorn
 */
@Getter
public class OozeChunkSection implements PalettedVolume {

  private static final int STORAGE_SIZE = OozeChunk.WIDTH
                                          * OozeChunk.SECTION_HEIGHT
                                          * OozeChunk.DEPTH;

  /**
   * The distance between the bottom (y=0) of the parent chunk and the bottom of the section.
   */
  private final int                altitude;
  private final BlockPalette       palette;
  private final BitCompactIntArray storage;

  private boolean isEmpty;
  private boolean shouldRecalculateIsEmpty;

  public OozeChunkSection(int altitude, BlockPalette palette, IntArray storage) {
    if (storage.size() != STORAGE_SIZE) {
      throw new IllegalArgumentException("Section storage must have exactly " +
                                         STORAGE_SIZE + " indices");
    }

    this.altitude = altitude;
    this.palette = palette;
    this.storage = BitCompactIntArray.fromIntArray(storage);

    // If the max storage value is zero, then we already know the section is empty.
    isEmpty = true;
    shouldRecalculateIsEmpty = (storage.maxValue() != 0);
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
  public int getWidth() {
    return OozeChunk.WIDTH;
  }

  @Override
  public int getHeight() {
    return OozeChunk.SECTION_HEIGHT;
  }

  @Override
  public int getDepth() {
    return OozeChunk.DEPTH;
  }

  @Override
  public BlockState getBlockAt(int x, int y, int z) {
    BlockState state = palette.getState(storage.get(getBlockIndex(x, y, z)));
    return state != null ? state : BlockState.DEFAULT;
  }

  /**
   * @return The index in the storage container where a block at the provided coordinates would be
   * stored.
   */
  private int getBlockIndex(int x, int y, int z) {
    int height = getHeight();
    return (y * height * height) + (z * getDepth()) + x;
  }

  /**
   * @return {@code true} if every block in the section {@link BlockState#isAir() is air}. Otherwise
   * {@code false}.
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public boolean isEmpty() {
    if (shouldRecalculateIsEmpty) {
      for (int blockIndex = 0; blockIndex < storage.size(); blockIndex++) {
        // Check if any block is not air.
        int stateId = storage.get(blockIndex);
        if (!palette.getState(stateId).isAir()) {
          isEmpty = false;
          break;
        }
      }
    }
    return isEmpty;
  }
}
