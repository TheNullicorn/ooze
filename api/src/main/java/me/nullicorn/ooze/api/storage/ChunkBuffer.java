package me.nullicorn.ooze.api.storage;

import java.io.IOException;
import me.nullicorn.ooze.api.world.BoundedLevel;
import me.nullicorn.ooze.api.world.Chunk;

/**
 * Temporary storage for chunks or chunk data. Implementing classes should effectively act as
 * bridges between NMS chunk storage and Ooze's level-based system.
 *
 * @author Nullicorn
 */
public interface ChunkBuffer<C extends Chunk> {

    /**
     * @return The level that provides the buffer with chunks. This is also where {@link #flush()
     * flushed chunks} are sent.
     */
    BoundedLevel<C> getLevel();

    /**
     * Moves any chunks in the buffer into its {@link #getLevel() level}, effectively emptying the
     * buffer.
     *
     * @return The number of chunks flushed out into the buffer's source.
     */
    int flush() throws IOException;

    /**
     * Discards any chunks stored in the buffer.
     *
     * @return The number of chunks cleared.
     */
    int clear();

    /**
     * @return The number of chunks in the buffer.
     */
    int size();
}
