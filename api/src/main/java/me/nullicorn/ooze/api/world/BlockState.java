package me.nullicorn.ooze.api.world;

import java.util.Objects;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.ooze.api.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the most basic aspects of a Minecraft block, such as its type and orientation.
 *
 * @author Nullicorn
 */
public final class BlockState {

    /**
     * A block state that can generally be used as a fallback when no other state can be found (for
     * whatever reason).
     */
    public static final BlockState DEFAULT = new BlockState(new ResourceLocation("air"));

    private final ResourceLocation name;
    @Nullable
    private final NBTCompound      properties;

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

    /**
     * @return The block's main identifier (e.g. "stone", "piston", etc).
     */
    public ResourceLocation getName() {
        return name;
    }

    /**
     * @return Any additional properties defining the state of the block (e.g. direction, power,
     * etc).
     */
    @Nullable
    public NBTCompound getProperties() {
        return properties;
    }

    /**
     * @return {@code true} if the state has {@link #getProperties() properties}. Otherwise {@code
     * false}.
     */
    public boolean hasProperties() {
        return properties != null;
    }

    /**
     * @return Whether or not this state represents any type of air.
     */
    public boolean isAir() {
        if (!name.getNamespace().equals("minecraft")) {
            return false;
        }

        String path = name.getPath();
        return path.equals("air") || path.equals("cave_air") || path.equals("void_air");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{name: \"").append(name).append("\"");
        if (hasProperties()) {
            builder.append(", properties: ").append(properties);
        }
        builder.append("}");
        return builder.toString();
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
