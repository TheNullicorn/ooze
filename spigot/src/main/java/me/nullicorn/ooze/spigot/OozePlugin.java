package me.nullicorn.ooze.spigot;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author Nullicorn
 */
public class OozePlugin extends JavaPlugin {

  private static final String GENERATOR_ID = "ooze";

  @Override
  public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
    if (id != null && id.equals(GENERATOR_ID)) {
      // TODO: 5/21/21 Create custom generator for loading chunks.
    }
    return null;
  }
}
