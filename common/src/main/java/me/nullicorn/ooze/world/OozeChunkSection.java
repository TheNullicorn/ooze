package me.nullicorn.ooze.world;

import lombok.Getter;
import me.nullicorn.ooze.serialize.IntArray;
import me.nullicorn.ooze.storage.BitCompactIntArray;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.storage.PalettedVolume;

/**
 * A 16x16x16 volume that stores some of the blocks of a {@link OozeChunk chunk}.
 *
 * @author Nullicorn
 */
public class OozeChunkSection implements PalettedVolume {

  private static final int STORAGE_SIZE = OozeChunk.WIDTH
                                          * OozeChunk.SECTION_HEIGHT
                                          * OozeChunk.DEPTH;

  @Getter
  private final BlockPalette       palette;
  @Getter
  private final BitCompactIntArray storage;

  public OozeChunkSection(BlockPalette palette, IntArray storage) {
    if (storage.size() != STORAGE_SIZE) {
      throw new IllegalArgumentException("Section storage must have exactly " +
                                         STORAGE_SIZE + " indices");

    } else if (storage.maxValue() < palette.size() - 1) {
      throw new IllegalArgumentException("Block storage is too small for its palette");
    }

    this.palette = palette;
    this.storage = BitCompactIntArray.fromIntArray(storage);
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
}
