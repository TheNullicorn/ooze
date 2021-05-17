package me.nullicorn.ooze.world;

import java.util.Objects;
import lombok.Getter;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.ooze.InvalidResourceLocationException;
import me.nullicorn.ooze.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the most basic aspects of a Minecraft block, such as its type and orientation.
 *
 * @author Nullicorn
 */
public class BlockState {

  /**
   * Constructs a block state from its serialized NBT format.
   *
   * @return The deserialized block state.
   * @throws InvalidBlockStateException       If the block state does not contain a {@code Name}.
   * @throws InvalidResourceLocationException If the block's name cannot be parsed.
   * @see #toNBT() The accepted NBT format
   */
  public static BlockState fromNBT(NBTCompound stateData)
      throws InvalidBlockStateException, InvalidResourceLocationException {
    String name = stateData.getString("Name", null);
    NBTCompound properties = stateData.getCompound("Properties");

    if (name == null) {
      throw new InvalidBlockStateException(stateData);
    }
    return new BlockState(ResourceLocation.fromString(name), properties);
  }

  /**
   * The block's main identifier (e.g. "stone", "piston", etc).
   */
  @Getter
  private final ResourceLocation name;

  /**
   * Any additional properties defining the state of the block (e.g. direction, power, etc).
   */
  @Nullable
  @Getter
  private final NBTCompound properties;

  /**
   * Constructs a block state without any additional {@link #getProperties() properties}.
   */
  public BlockState(ResourceLocation name) {
    this(name, null);
  }

  public BlockState(ResourceLocation name, @Nullable NBTCompound properties) {
    this.name = name;
    this.properties = properties;
  }

  public boolean hasProperties() {
    return properties != null;
  }

  /**
   * The following format is used:
   * <pre>
   *   {@code
   *   {
   *      Name: String,
   *      Properties: {
   *        [property]: String
   *      }?
   *   }
   *   }
   * </pre>
   *
   * @return A new NBT compound representing this block state.
   */
  public NBTCompound toNBT() {
    NBTCompound stateData = new NBTCompound();
    stateData.put("Name", name.toString());
    if (properties != null) {
      stateData.put("Properties", properties);
    }
    return stateData;
  }

  @Override
  public String toString() {
    return toNBT().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlockState that = (BlockState) o;
    return name.equals(that.name) &&
           Objects.equals(properties, that.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, properties);
  }
}
