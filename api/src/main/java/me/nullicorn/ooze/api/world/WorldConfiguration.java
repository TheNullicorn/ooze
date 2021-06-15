package me.nullicorn.ooze.api.world;

/**
 * Options that determine how worlds are saved and loaded by Ooze.
 *
 * @author Nullicorn
 */
public interface WorldConfiguration {

    /**
     * {@code true} if worlds should save modified chunks, entities, etc. Otherwise {@code false}.
     */
    boolean isSavingEnabled();

    /**
     * {@code true} if worlds should automatically save at whatever interval is specified by {@link
     * #getAutoSaveInterval()}. {@code false} if {@link #isSavingEnabled() saving} is disabled, or
     * if worlds should only be saved when unloaded.
     */
    boolean isAutoSaveEnabled();

    /**
     * {@code true} if only chunks within defined boundaries should be loaded and saved. Otherwise
     * {@code false}.
     * <p><br>
     * If this is {@code true}, then the fixed boundaries are specified by the properties {@link
     * #getFixedOrigin() origin}, {@link #getFixedWidth() width}, and {@link #getFixedDepth()
     * depth}.
     * <p><br>
     * If this is {@code false}, then the world's dimensions will be dynamic, scaling to fit any
     * chunks that contain blocks. The hard limit on dynamic-sized worlds is 255x255 chunks.
     */
    boolean isWorldSizeFixed();

    /**
     * How often, in seconds, worlds should be automatically saved. The auto-save timer is kept
     * per-world, so multiple worlds using the same interval may auto-save at different times
     * depending on when they were loaded.
     * <p><br>
     * If {@link #isAutoSaveEnabled() isAutoSaveEnabled} is {@code false}, then this will always be
     * zero ({@code 0}).
     */
    int getAutoSaveInterval();

    /**
     * The lowest X and Z coordinates that will be saved or loaded for fixed-size worlds. Measured
     * in chunks.
     * <p><br>
     * If {@link #isWorldSizeFixed() isWorldSizeFixed} is {@code false}, then this will default to
     * the world origin, {@code (0, 0)}.
     */
    Location2D getFixedOrigin();

    /**
     * The fixed size of the world along the X axis. Measured in chunks.
     * <p><br>
     * If {@link #isWorldSizeFixed() isWorldSizeFixed} is {@code false}, then this will always be
     * zero ({@code 0}).
     */
    int getFixedWidth();

    /**
     * The fixed size of the world along the Z axis. Measured in chunks.
     * <p><br>
     * If {@link #isWorldSizeFixed() isWorldSizeFixed} is {@code false}, then this will always be
     * zero ({@code 0}).
     */
    int getFixedDepth();
}
