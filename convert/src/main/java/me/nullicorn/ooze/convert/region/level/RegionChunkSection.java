package me.nullicorn.ooze.convert.region.level;

import java.io.IOException;
import lombok.Getter;
import me.nullicorn.ooze.serialize.OozeDataOutputStream;
import me.nullicorn.ooze.serialize.OozeSerializable;

/**
 * @author Nullicorn
 */
public class RegionChunkSection implements OozeSerializable {

  /**
   * How high from the bottom of the chunk this section is, measured in units of 16 blocks.
   */
  @Getter
  private final int altitude;

  RegionChunkSection(int altitude) {
    if (altitude < 0 || altitude >= RegionChunk.SECTIONS_PER_CHUNK) {
      throw new IllegalArgumentException("Invalid chunk section altitude: " + altitude);
    }

    this.altitude = altitude;
  }

  public boolean isEmpty() {
    // TODO: 5/16/21
    return false;
  }

  private void evaluateContents() {
    // TODO: 5/16/21 Use this to determine whether or not the chunk is empty.
  }

  @Override
  public void serialize(OozeDataOutputStream out) throws IOException {
    // TODO: 5/16/21
  }
}
