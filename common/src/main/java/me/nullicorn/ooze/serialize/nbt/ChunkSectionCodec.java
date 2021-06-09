package me.nullicorn.ooze.serialize.nbt;

import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.nedit.type.TagType;
import me.nullicorn.ooze.serialize.Codec;
import me.nullicorn.ooze.serialize.CodingException;
import me.nullicorn.ooze.storage.BitCompactIntArray;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.storage.WordedIntArray;
import me.nullicorn.ooze.world.OozeChunkSection;

/**
 * NBT serialization for {@link OozeChunkSection chunk sections}.
 *
 * @author Nullicorn
 */
public class ChunkSectionCodec implements Codec<OozeChunkSection, NBTCompound> {

  private static final BlockPaletteCodec defaultPaletteCodec = new BlockPaletteCodec();

  // - PALETTE_ADDED is the data version when sections began using palettes instead of absolute block
  //   IDs.
  // - BLOCKS_PADDED is the data version when values in the "BlockStates" array could no longer be
  //   stored across multiple longs.
  private static final int DATA_VERSION_PALETTE_ADDED = 1451;
  private static final int DATA_VERSION_BLOCKS_PADDED = 2527;

  // NBT tag names used by Minecraft.
  private static final String TAG_ALTITUDE     = "Y";
  private static final String TAG_PALETTE      = "Palette";
  private static final String TAG_BLOCK_STATES = "BlockStates";

  private final Codec<BlockPalette, NBTList> paletteCodec;
  private final boolean                      usePalettes;
  private final boolean                      usePaddedBlockStates;

  /**
   * Constructs a new chunk section codec that codes block palettes using the {@link
   * BlockPaletteCodec built-in codec}.
   */
  public ChunkSectionCodec(int dataVersion) {
    this(dataVersion, defaultPaletteCodec);
  }

  /**
   * @param dataVersion  The Minecraft data version that the codec should target. To use encodings
   *                     prior to the introduction of data versions, set this to {@code 99} or
   *                     lower.
   * @param paletteCodec The codec to use for encoding and decoding the sections' block palettes, if
   *                     applicable for the used {@code dataVersion}.
   */
  public ChunkSectionCodec(int dataVersion, BlockPaletteCodec paletteCodec) {
    this.paletteCodec = paletteCodec;
    usePalettes = dataVersion >= DATA_VERSION_PALETTE_ADDED;
    usePaddedBlockStates = dataVersion >= DATA_VERSION_BLOCKS_PADDED;
  }

  @Override
  public NBTCompound encode(OozeChunkSection section) throws CodingException {
    int altitude = section.getAltitude();
    if (altitude < Byte.MIN_VALUE || altitude > Byte.MAX_VALUE) {
      throw new CodingException("Section altitude does not fit in a signed byte: " + altitude);
    }

    // Make a copy of the section's storage to be localized.
    // Localization in this context means separating the section's palette from the chunk's, so that
    // the new palette and storage array only contain values that are actually used in this specific
    // section.
    BitCompactIntArray localizedStorage = new BitCompactIntArray(section.getStorage());
    BlockPalette localizedPalette = section.getPalette().extract(localizedStorage);

    NBTCompound encoded = new NBTCompound();
    if (usePalettes) {
      WordedIntArray wordedStorage = WordedIntArray.fromIntArray(localizedStorage);

      encoded.put(TAG_PALETTE, paletteCodec.encode(localizedPalette));
      encoded.put(TAG_BLOCK_STATES, wordedStorage.toRaw(!usePaddedBlockStates));
    } else {
      // TODO: 6/9/21 Add support for legacy section encoding.
      throw new UnsupportedOperationException("Legacy section encoding not yet supported");
    }

    return encoded;
  }

  @Override
  public OozeChunkSection decode(NBTCompound encoded) throws CodingException {
    if (!encoded.containsKey(TAG_ALTITUDE)) {
      throw new CodingException("Chunk section has no altitude");
    }

    // The section's y-value (in 16-block units).
    int altitude = encoded.getInt(TAG_ALTITUDE, 0);

    // Check if the section uses the modern format (palette & long-array storage).
    if (usePalettes) {
      // Ensure the section isn't empty.
      if (isEncodedEmpty(encoded)) {
        return new OozeChunkSection(altitude, new BlockPalette(), new BitCompactIntArray(4096, 0));
      }

      // Read the palette & storage containers.
      BlockPalette palette = paletteCodec.decode(encoded.getList(TAG_PALETTE));
      WordedIntArray storage = WordedIntArray.fromRaw(
          encoded.getLongArray(TAG_BLOCK_STATES),
          4096,
          palette.size() - 1,
          !usePaddedBlockStates);
      return new OozeChunkSection(altitude, palette, storage);
    } else {
      // TODO: 6/9/21 Add support for legacy section encoding.
      throw new UnsupportedOperationException("Legacy section decoding not yet supported");
    }
  }

  /**
   * @return {@code true} if the {@code encoded} section is {@code null} or missing block / palette
   * data. Otherwise {@code false}.
   */
  private boolean isEncodedEmpty(NBTCompound encoded) {
    //noinspection ConstantConditions
    return encoded == null
           || !encoded.containsTag(TAG_PALETTE, TagType.LIST)
           || encoded.getList(TAG_PALETTE).isEmpty()
           || !encoded.containsTag(TAG_BLOCK_STATES, TagType.LONG_ARRAY);
  }
}
