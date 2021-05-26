package me.nullicorn.ooze.convert.region.world;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.world.OozeLevel;
import me.nullicorn.ooze.convert.ConversionException;
import me.nullicorn.ooze.convert.LegacyUtil;
import me.nullicorn.ooze.convert.region.NibbleArray;
import me.nullicorn.ooze.convert.region.file.ChunkSource;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.storage.WordedIntArray;
import me.nullicorn.ooze.storage.PaletteUpgrader;
import me.nullicorn.ooze.storage.BitCompactIntArray;
import me.nullicorn.ooze.world.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A tool for constructing levels by allowing chunks to be added individually or in groups.
 *
 * @author Nullicorn
 */
public class LevelBuilder {

  // TODO: 5/21/21 Make entities toggleable.
  // TODO: 5/21/21 Provide access to "custom" NBT data.

  // The data version when sections began using palettes instead of absolute block IDs.
  private static final int PALETTE_ADDED_DATA_VERSION = 1451;

  // The data version when values in the "BlockStates" array could no longer be stored across
  // multiple longs.
  private static final int BLOCKS_PADDED_DATA_VERSION = 2527;

  @Getter
  final ChunkSource source;

  final Set<Location2D> chunksToLoad;

  public LevelBuilder(ChunkSource source) {
    this.source = source;
    chunksToLoad = new HashSet<>();
  }

  /**
   * Adds the chunk at chunk coordinates ({@code chunkX}, {@code chunkZ}) to the world, if it exists
   * in the {@link #getSource() source}.
   */
  public LevelBuilder addChunk(int chunkX, int chunkZ) {
    return addChunk(new Location2D(chunkX, chunkZ));
  }

  /**
   * Adds the chunk at the provided {@code chunkLoc} to the world, if it exists in the {@link
   * #getSource() source}.
   */
  public LevelBuilder addChunk(Location2D chunkLoc) {
    chunksToLoad.add(chunkLoc);
    return this;
  }

  /**
   * Same as {@link #addChunks(int, int, int, int)}, but {@code minChunkX} and {@code minChunkZ} are
   * provided via a single location, {@code minChunkLoc}.
   *
   * @see #addChunks(int, int, int, int)
   */
  public LevelBuilder addChunks(Location2D minChunkLoc, int width, int depth) {
    return addChunks(minChunkLoc.getX(), minChunkLoc.getZ(), width, depth);
  }

  /**
   * Adds all existing chunks in a {@code width * depth} area to the world.
   *
   * @param minChunkX The lowest possible x-coordinate of any chunk that should be added; measured
   *                  in chunks.
   * @param minChunkZ The lowest possible z-coordinate of any chunk that should be added; measured
   *                  in chunks.
   */
  public LevelBuilder addChunks(int minChunkX, int minChunkZ, int width, int depth) {
    int maxChunkX = minChunkX + width;
    int maxChunkZ = minChunkZ + depth;
    for (int chunkX = minChunkX; chunkX < maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ < maxChunkZ; chunkZ++) {
        addChunk(chunkX, chunkZ);
      }
    }
    return this;
  }

  /**
   * Compiles all requested chunks into a single level.
   *
   * @throws IOException           If chunk data could not be retrieved from the {@link #getSource()
   *                               source}, or if any of the chunk data was corrupted.
   * @throws IllegalStateException If the location of any requested chunk lies outside the level's
   *                               bounds.
   */
  public OozeLevel build() throws IOException {
    OozeLevel level = new OozeLevel();
    for (Location2D chunkPos : chunksToLoad) {
      NBTCompound chunkData = source.loadChunk(chunkPos);
      if (chunkData != null) {
        try {
          level.storeChunk(createChunk(chunkData));
        } catch (IllegalArgumentException e) {
          throw new IllegalStateException("Cannot build level with out-of-bound chunk", e);
        }
      }
    }
    return level;
  }

  // Viewer beware, you're in for a scare...
  // Chunk NBT deserialization vvv

  /**
   * Constructs a chunk from its serialized NBT format.
   *
   * @throws ConversionException If the chunk data is corrupted.
   */
  private RegionChunk createChunk(NBTCompound data) throws ConversionException {
    // Version that the chunk was last saved in.
    int dataVersion = data.getInt("DataVersion", 99);

    data = data.getCompound("Level");
    if (data == null) {
      throw new ConversionException("Chunk data is missing \"Level\" field");
    }

    // Absolute position of the chunk.
    if (!data.containsKey("xPos") || !data.containsKey("zPos")) {
      throw new ConversionException("Chunk data is missing location information");
    }
    Location2D chunkPos = new Location2D(data.getInt("xPos", 0), data.getInt("zPos", 0));

    // Store the chunk's 16x16x16 block sections.
    RegionChunk chunk = new RegionChunk(chunkPos, dataVersion);
    NBTList sections = data.getList("Sections");
    if (sections != null) {
      for (Object element : sections) {
        if (!(element instanceof NBTCompound)) {
          continue;
        }

        NBTCompound sectionData = (NBTCompound) element;
        if (!sectionData.containsKey("Y")) {
          throw new ConversionException("Chunk section is missing altitude value");
        }

        // Ignore sections at invalid heights.
        int altitude = sectionData.getInt("Y", -1);
        if (altitude >= 0 && altitude < RegionChunk.SECTIONS_PER_CHUNK) {
          chunk.setSection(altitude, createChunkSection(sectionData, dataVersion));
        }
      }
    }

    // Store the chunk's entities.
    NBTList entities = data.getList("Entities");
    if (entities != null) {
      chunk.getEntities().addAll(entities);
    }

    // Store the chunk's block entities.
    NBTList blockEntities = data.getList("TileEntities");
    if (blockEntities != null) {
      chunk.getBlockEntities().addAll(blockEntities);
    }

    return chunk;
  }

  /**
   * Constructs chunk section from its serialized NBT format.
   *
   * @return A chunk section containing the blocks from the {@code data} source, or {@code null} if
   * the data represents an empty section.
   * @throws ConversionException If the section contains corrupted data.
   */
  @Nullable
  private RegionChunkSection createChunkSection(NBTCompound data, int dataVersion)
      throws ConversionException {

    if (dataVersion > PALETTE_ADDED_DATA_VERSION) {
      // Section uses the modern, paletted format.

      // Ensure the section isn't empty.
      if (!data.containsKey("Palette")
          || !data.containsKey("BlockStates")) {
        return null;
      }

      // noinspection ConstantConditions
      BlockPalette palette = createPalette(data.getList("Palette"));
      WordedIntArray storage = WordedIntArray.fromRaw(
          data.getLongArray("BlockStates"),
          4096,
          palette.size() - 1,
          dataVersion >= BLOCKS_PADDED_DATA_VERSION);
      return new RegionChunkSection(palette, storage);
    } else {
      // Section uses pre-1.13, absolute storage format.
      return createLegacySection(data);
    }
  }

  /**
   * Same as {@link #createChunkSection(NBTCompound, int)}, but attempts to read the chunk data
   * using the legacy storage format. This format was used before Minecraft 1.13 and uses absolute
   * block IDs instead of a palette.
   */
  @Nullable
  private RegionChunkSection createLegacySection(NBTCompound data) throws ConversionException {
    byte[] rawBlocks = data.getByteArray("Blocks");
    byte[] rawOverflowBlocks = data.getByteArray("Add");
    byte[] rawData = data.getByteArray("Data");

    // Ensure the section isn't empty & the block data isn't corrupted.
    if (rawBlocks == null) {
      return null;
    } else if (rawBlocks.length != 4096
               || (rawData != null && rawData.length != 2048)
               || (rawOverflowBlocks != null && rawOverflowBlocks.length != 2048)) {
      throw new ConversionException("Chunk contains corrupted block data");
    }

    NibbleArray overflowArray = rawOverflowBlocks != null
        ? NibbleArray.fromBytes(rawOverflowBlocks, 4096)
        : null;

    NibbleArray stateArray = rawData != null
        ? NibbleArray.fromBytes(rawData, 4096)
        : null;

    int maxState = LegacyUtil.getHighestCompoundState();
    BlockPalette palette = new BlockPalette();
    PaletteUpgrader upgrader = new PaletteUpgrader(maxState);

    // Compact array is used so that it can be passed to upgrader#upgrade()
    BitCompactIntArray tempStorage = new BitCompactIntArray(4096, maxState);
    for (int i = 0; i < rawBlocks.length; i++) {
      int blockId = rawBlocks[i];
      if (overflowArray != null) {
        blockId |= overflowArray.get(i) << 8;
      }
      int blockData = stateArray != null ? stateArray.get(i) : 0;

      // Map the legacy ID to its new palette index.
      BlockState state = LegacyUtil.getBlockStateFromLegacy(blockId, blockData);
      tempStorage.set(i, blockId);
      upgrader.registerChange(blockId, palette.addState(state));
    }
    upgrader.lock();

    // Apply the legacy->paletted ID change.
    upgrader.upgrade(tempStorage);
    WordedIntArray storage = new WordedIntArray(tempStorage.size(), tempStorage.maxValue());

    return new RegionChunkSection(palette, storage);
  }

  /**
   * Constructs a new block palette from its serialized NBT form. The first item in the provided
   * list is used as the palette's default state.
   */
  private BlockPalette createPalette(NBTList data) throws ConversionException {
    BlockPalette palette = null;
    boolean isFirstElement = true;

    for (Object element : data) {
      if (!(element instanceof NBTCompound)) {
        throw new ConversionException("Cannot create BlockState from " + element.getClass());
      }

      BlockState state = BlockState.fromNBT((NBTCompound) element);

      // Use the first state in the list as the palette's default.
      if (isFirstElement) {
        palette = new BlockPalette(state);
        isFirstElement = false;
      } else {
        palette.addState(state);
      }
    }

    return palette == null ? new BlockPalette() : palette;
  }
}
