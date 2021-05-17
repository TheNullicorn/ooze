package me.nullicorn.ooze.convert.region.level;

import java.io.IOException;
import lombok.Getter;
import me.nullicorn.ooze.Location2D;
import me.nullicorn.ooze.serialize.OozeDataOutputStream;
import me.nullicorn.ooze.world.Chunk;

/**
 * @author Nullicorn
 */
public class RegionChunk implements Chunk {

  static final int SECTIONS_PER_CHUNK = 16;

  @Getter
  private final Location2D           location;
  @Getter
  private final int                  dataVersion;
  private final RegionChunkSection[] sections;

  public RegionChunk(Location2D location, int dataVersion) {
    this.location = location;
    this.dataVersion = dataVersion;
    sections = new RegionChunkSection[SECTIONS_PER_CHUNK];
  }

  @Override
  public boolean isEmpty() {
    for (RegionChunkSection section : sections) {
      if ((section != null && !section.isEmpty())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void serialize(OozeDataOutputStream out) throws IOException {
    // TODO: 5/16/21
  }
}
