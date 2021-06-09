package me.nullicorn.ooze.serialize.nbt;

import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.nedit.type.TagType;
import me.nullicorn.ooze.serialize.Codec;
import me.nullicorn.ooze.serialize.CodingException;
import me.nullicorn.ooze.storage.BlockPalette;
import me.nullicorn.ooze.world.BlockState;

/**
 * NBT serialization for {@link BlockPalette block palettes}.
 *
 * @author Nullicorn
 */
public class BlockPaletteCodec implements Codec<BlockPalette, NBTList> {

  private static final BlockStateCodec defaultBlockCodec = new BlockStateCodec();

  private final Codec<BlockState, NBTCompound> blockCodec;

  /**
   * Constructs a new palette codec that codes block states using the {@link BlockStateCodec
   * built-in codec}.
   */
  public BlockPaletteCodec() {
    this.blockCodec = defaultBlockCodec;
  }

  /**
   * @param blockStateCodec The codec to use for encoding and decoding the block states contained in
   *                        a palette.
   */
  public BlockPaletteCodec(Codec<BlockState, NBTCompound> blockStateCodec) {
    this.blockCodec = blockStateCodec;
  }

  @Override
  public NBTList encode(BlockPalette value) throws CodingException {
    NBTList encoded = new NBTList(TagType.COMPOUND);
    for (BlockState state : value) {
      encoded.add(blockCodec.encode(state));
    }
    return encoded;
  }

  @Override
  public BlockPalette decode(NBTList value) throws CodingException {
    if (value == null) {
      throw new IllegalArgumentException("Cannot decode null palette");
    } else if (value.getContentType() != TagType.COMPOUND) {
      throw new CodingException("Cannot decode palette from list of " + value.getContentType());
    }

    BlockPalette palette = null;
    for (Object element : value) {
      BlockState state = blockCodec.decode((NBTCompound) element);

      // The first element should always be the default state for the palette.
      // All states that follow should be added normally.
      if (palette == null) {
        palette = new BlockPalette(state);
      } else {
        palette.addState(state);
      }
    }

    return palette != null
        ? palette
        : new BlockPalette();
  }
}
