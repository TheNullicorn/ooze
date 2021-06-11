package me.nullicorn.ooze.serialize.nbt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.nedit.type.NBTList;
import me.nullicorn.nedit.type.TagType;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.serialize.Codec;
import me.nullicorn.ooze.serialize.CodingException;
import me.nullicorn.ooze.world.OozeChunk;
import me.nullicorn.ooze.world.OozeChunkSection;
import me.nullicorn.ooze.world.OozeLevel;

/**
 * NBT serialization for {@link OozeChunk chunks}.
 *
 * @author Nullicorn
 */
// TODO: 6/9/21 Add biome support.
public class ChunkCodec implements Codec<OozeChunk, NBTCompound> {

  private static final String TAG_DATA_VERSION   = "DataVersion";
  private static final String TAG_CHUNK          = "Level";
  private static final String TAG_SECTIONS       = "Sections";
  private static final String TAG_CHUNK_X        = "xPos";
  private static final String TAG_CHUNK_Z        = "zPos";
  private static final String TAG_ENTITIES       = "Entities";
  private static final String TAG_BLOCK_ENTITIES = "TileEntities";

  private final OozeLevel            level;
  private final SectionCodecProvider sectionCodecProvider;

  /**
   * Same as the {@link ChunkCodec#ChunkCodec(OozeLevel, SectionCodecProvider) other constructor},
   * but a new section codec is created each time a chunk is encoded or decoded.
   * <p><br>
   * It is recommended instead to supply a custom {@link SectionCodecProvider SectionCodecProvider}
   * that caches section codecs by {@code dataVersion} for reuse. A basic implementation is
   * available via {@link PooledSectionCodecProvider PooledSectionCodecProvider}.
   */
  public ChunkCodec(OozeLevel level) {
    // Creates a new codec each time the provider is called.
    this(level, ChunkSectionCodec::new);
  }

  /**
   * @param level                The level that entities and block entities in coded chunks will be
   *                             added to and taken from.
   * @param sectionCodecProvider May be used during encoding & decoding operations to get a codec
   *                             that can process sections in a chunk's correct {@code dataVersion}.
   *                             If the chunk codec will be used frequently, it is recommended that
   *                             this provider caches/pools its results for reuse.
   * @see SectionCodecProvider
   */
  public ChunkCodec(OozeLevel level, SectionCodecProvider sectionCodecProvider) {
    this.level = level;
    this.sectionCodecProvider = sectionCodecProvider;
  }

  @Override
  public NBTCompound encode(OozeChunk chunk) throws CodingException {
    int dataVersion = chunk.getDataVersion();
    Location2D chunkLoc = chunk.getLocation();

    NBTCompound chunkTag = new NBTCompound();
    NBTCompound rootTag = new NBTCompound();

    // The root tag contains the chunk tag & data version tag.
    rootTag.put(TAG_DATA_VERSION, chunk.getDataVersion());
    rootTag.put(TAG_CHUNK, chunkTag);

    // Write the chunk's location, sections, block entities, & entities.
    chunkTag.put(TAG_CHUNK_X, chunkLoc.getX());
    chunkTag.put(TAG_CHUNK_Z, chunkLoc.getZ());
    chunkTag.put(TAG_SECTIONS, encodeSections(dataVersion, chunk));
    chunkTag.put(TAG_ENTITIES, level.getEntities(chunkLoc));
    chunkTag.put(TAG_BLOCK_ENTITIES, level.getBlockEntities(chunkLoc));

    // Setting this to "light" should ensure that newer versions of the game perform light and
    // heightmap calculations for us before the chunk is loaded.
    chunkTag.put("Status", "light");

    return rootTag;
  }

  @Override
  public OozeChunk decode(NBTCompound root) throws CodingException {
    // Read the chunk's data-version & main tag.
    int dataVersion = root.getInt(TAG_DATA_VERSION, 99);
    NBTCompound encoded = root.getCompound(TAG_CHUNK);

    if (encoded == null) {
      throw new CodingException("Encoded chunk has no data");
    }

    // Read the chunk's position & block sections.
    Number chunkX = encoded.getNumber(TAG_CHUNK_X, null);
    Number chunkZ = encoded.getNumber(TAG_CHUNK_Z, null);
    Set<OozeChunkSection> sections = decodeSections(dataVersion, encoded.getList(TAG_SECTIONS));

    if (chunkX == null || chunkZ == null) {
      throw new CodingException("Encoded chunk has no position information");
    }
    Location2D chunkLoc = new Location2D(chunkX.intValue(), chunkZ.intValue());

    // Initialize the chunk with its decoded sections.
    OozeChunk chunk = new OozeChunk(chunkLoc, dataVersion);
    for (OozeChunkSection section : sections) {
      chunk.setSection(section.getAltitude(), section);
    }

    // Replace any existing entities & block entities stored in the level for the chunk.
    level.setEntities(chunkLoc, encoded.getList(TAG_ENTITIES));
    level.setBlockEntities(chunkLoc, encoded.getList(TAG_BLOCK_ENTITIES));

    return chunk;
  }

  /**
   * Encodes all of the {@code chunk}'s sections into a single list.
   *
   * @param dataVersion The data version that the returned list should be compatible with.
   */
  private NBTList encodeSections(int dataVersion, OozeChunk chunk) throws CodingException {
    // Retrieve a codec to be used on each section in the chunk.
    Codec<OozeChunkSection, NBTCompound> sectionCodec = sectionCodecProvider.apply(dataVersion);

    // Encode & compile the chunk's sections into one list.
    NBTList encoded = new NBTList(TagType.COMPOUND);
    for (OozeChunkSection section : chunk) {
      encoded.add(sectionCodec.encode(section));
    }

    return encoded;
  }

  /**
   * Opposite of {@link #encodeSections(int, OozeChunk)}.
   */
  private Set<OozeChunkSection> decodeSections(int dataVersion, NBTList encoded)
      throws CodingException {
    Set<OozeChunkSection> sections = new HashSet<>();

    if (encoded == null) {
      // Nothing to decode.
      return sections;
    } else if (encoded.getContentType() != TagType.COMPOUND) {
      // Sections must be a list of compounds.
      throw new CodingException("Cannot decode sections from " + encoded.getContentType());
    }

    // Retrieve a codec to be used on each section in the chunk.
    Codec<OozeChunkSection, NBTCompound> sectionCodec = sectionCodecProvider.apply(dataVersion);
    for (Object entry : encoded) {
      OozeChunkSection section = sectionCodec.decode((NBTCompound) entry);
      if (section.getPalette().size() > 1 || !section.getPalette().getState(0).isAir()) {
        sections.add(section);
      }
    }

    return sections;
  }

  /**
   * A simple function that accepts a single Minecraft {@code dataVersion} and returns a {@link
   * Codec} that is capable of coding sections for that version.
   */
  public interface SectionCodecProvider extends IntFunction<Codec<OozeChunkSection, NBTCompound>> {

    /**
     * @param dataVersion The Minecraft data version that the returned codec will support, or
     *                    anything less than {@code 100} for a codec that supports legacy data
     *                    without a version.
     * @return A chunk section codec that supports the provided {@code dataVersion}.
     */
    @Override
    Codec<OozeChunkSection, NBTCompound> apply(int dataVersion);
  }

  /**
   * A very simple {@link SectionCodecProvider section codec provider} that creates codecs as
   * needed, and pools them for reuse.
   * <p><br>
   * The pool can be cleared at any time using {@link #clear()}.
   */
  public static class PooledSectionCodecProvider implements SectionCodecProvider {

    // I hate boxed types, but this shouldn't have any impact as long as the provider is used
    // correctly.
    private final Map<Integer, ChunkSectionCodec> codecsByVersion = new HashMap<>();

    @Override
    public Codec<OozeChunkSection, NBTCompound> apply(int dataVersion) {
      return codecsByVersion.computeIfAbsent(dataVersion, ChunkSectionCodec::new);
    }

    /**
     * Clear the pool of section codecs.
     */
    public void clear() {
      codecsByVersion.clear();
    }
  }
}
