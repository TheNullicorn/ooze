package me.nullicorn.ooze.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map.Entry;
import me.nullicorn.nedit.SNBTReader;
import me.nullicorn.nedit.type.NBTCompound;
import me.nullicorn.ooze.InvalidResourceLocationException;
import me.nullicorn.ooze.world.BlockState;
import me.nullicorn.ooze.world.InvalidBlockStateException;

/**
 * Helper methods for working with data from older Minecraft versions.
 *
 * @author Nullicorn
 */
public final class LegacyUtil {

  private static       HashMap<Integer, BlockState> legacyToModern;
  private static       int                          highestCompoundId = -1;
  private static final Object                       lock              = new Object();

  /**
   * @return A 16-bit integer, with the higher 12 bits holding the ID of the highest legacy block
   * state, and the lowest 4 bits containing the highest data value for that state.
   */
  public static int getHighestCompoundState() {
    // Only load legacy data if needed.
    synchronized (lock) {
      if (legacyToModern == null) {
        loadLegacyBlocks();
      }
    }
    return highestCompoundId;
  }

  /**
   * Same as {@link #getBlockStateFromLegacy(int, int)}, but {@code data} defaults to {@code 0}.
   */
  public static BlockState getBlockStateFromLegacy(int id) {
    return getBlockStateFromLegacy(id, 0);
  }

  /**
   * @return The modern block state that replaces blocks that previously had the {@code id} and
   * {@code data} in versions before 1.13. If the modern state cannot be found, {@link
   * BlockState#DEFAULT} is returned.
   */
  public static BlockState getBlockStateFromLegacy(int id, int data) {
    // Only load legacy data if needed.
    synchronized (lock) {
      if (legacyToModern == null) {
        loadLegacyBlocks();
      }
    }

    BlockState modernState = legacyToModern.get((id << 4) | (data & 0xF));
    if (modernState == null) {
      return BlockState.DEFAULT;
    }
    return modernState;
  }

  /**
   * Initializes and populates the {@link #legacyToModern} mappings.
   */
  private static void loadLegacyBlocks() {
    JsonElement jsonBlockMap = loadJsonResource("/block_to_modern.json");
    if (!jsonBlockMap.isJsonObject()) {
      throw new JsonParseException("legacy->modern block mappings must be a JSON object");
    }

    legacyToModern = new HashMap<>();
    highestCompoundId = -1;
    try {
      for (Entry<String, JsonElement> entry : jsonBlockMap.getAsJsonObject().entrySet()) {
        NBTCompound rawState = SNBTReader.readCompound(entry.getValue().getAsString());

        int legacy = Integer.parseInt(entry.getKey());
        BlockState modern = BlockState.fromNBT(rawState);
        legacyToModern.put(legacy, modern);

        // Update highest known ID if necessary.
        if (legacy > highestCompoundId) {
          highestCompoundId = legacy;
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InvalidBlockStateException | InvalidResourceLocationException e) {
      throw new UncheckedIOException(new IOException("Failed to load legacy block state", e));
    }
  }

  /**
   * Loads a file from the jar's resources and parses its contents as a JSON document.
   *
   * @param name The resource's name within the jar.
   * @return The parsed JSON resource.
   * @throws JsonParseException If the resource could not be parsed as JSON.
   */
  private static JsonElement loadJsonResource(String name) {
    InputStream resourceStream = LegacyUtil.class.getResourceAsStream(name);
    return JsonParser.parseReader(new InputStreamReader(resourceStream));
  }

  private LegacyUtil() {
    throw new UnsupportedOperationException("LegacyUtil should not be instantiated");
  }
}
