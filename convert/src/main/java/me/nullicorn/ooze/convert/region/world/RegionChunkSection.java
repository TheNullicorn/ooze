package me.nullicorn.ooze.convert.region.world;

import lombok.Getter;
import me.nullicorn.ooze.convert.region.PaddedIntArray;
import me.nullicorn.ooze.serialize.IntArray;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.storage.PalettedVolume;
import me.nullicorn.ooze.world.BlockState;

/**
 * A 16x16x16 volume that stores some of the blocks of a {@link RegionChunk region chunk}.
 *
 * @author Nullicorn
 */
public class RegionChunkSection implements PalettedVolume<BlockPalette, PaddedIntArray> {

  private static final int STORAGE_SIZE = RegionChunk.WIDTH
                                          * RegionChunk.SECTION_HEIGHT
                                          * RegionChunk.DEPTH;

  @Getter
  private final BlockPalette palette;

  @Getter
  private final PaddedIntArray storage;

  // Cached result of the last call to isEmpty().
  private boolean isEmpty = true;

  // Whether or not isEmpty needs to be recalculated.
  private boolean modifiedSinceEmptyCheck = true;

  public RegionChunkSection() {
    this(new BlockPalette(), new PaddedIntArray(STORAGE_SIZE, 0));
  }

  public RegionChunkSection(BlockPalette palette, IntArray storage) {
    if (storage.size() != STORAGE_SIZE) {
      throw new IllegalArgumentException("Section storage must have exactly " +
                                         STORAGE_SIZE + " indices");

    } else if (storage.maxValue() < palette.size() - 1) {
      throw new IllegalArgumentException("Block storage is too small for its palette");
    }

    this.palette = palette;

    if (storage instanceof PaddedIntArray) {
      this.storage = (PaddedIntArray) storage;
    } else {
      PaddedIntArray compact = new PaddedIntArray(storage.size(), storage.maxValue());
      storage.forEach(compact::set);
      this.storage = compact;
    }
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
    return RegionChunk.WIDTH;
  }

  @Override
  public int getHeight() {
    return RegionChunk.SECTION_HEIGHT;
  }

  @Override
  public int getDepth() {
    return RegionChunk.DEPTH;
  }

  @Override
  public BlockState getBlockAt(int x, int y, int z) {
    BlockState state = palette.getState(storage.get(getBlockIndex(x, y, z)));
    return state != null ? state : BlockState.DEFAULT;
  }

  @Override
  public BlockState setBlockAt(int x, int y, int z, BlockState state) {
    int stateId = palette.getOrAddStateId(state);
    BlockState previousState = palette.getState(storage.set(getBlockIndex(x, y, z), stateId));

    if (stateId != palette.getDefaultStateId()) {
      modifiedSinceEmptyCheck = true;
    }
    return previousState != null ? previousState : BlockState.DEFAULT;
  }

  @Override
  public boolean isEmpty() {
    if (!modifiedSinceEmptyCheck) {
      return isEmpty;
    }

    int defaultStateId = palette.getDefaultStateId();
    isEmpty = true;

    for (int i = 0; i < storage.size(); i++) {
      if (storage.get(i) != defaultStateId) {
        isEmpty = false;
        break;
      }
    }

    return isEmpty;
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
