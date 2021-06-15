package me.nullicorn.ooze.api.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import me.nullicorn.ooze.api.storage.ChunkBuffer;
import org.bukkit.World;

/**
 * @author Nullicorn
 */
public interface WorldTools {

    BoundedLevel<?> readLevel(InputStream source) throws IOException;

    void writeLevel(BoundedLevel<?> level, OutputStream destination) throws IOException;

    // TODO: 6/14/21 Add a wrapper exception for failing to create a world.
    //       In the default implementation, this will probably be IOException and some forms of
    //       reflection exceptions.
    World createWorld(ChunkBuffer<?> chunkLoader) throws Exception;
}
