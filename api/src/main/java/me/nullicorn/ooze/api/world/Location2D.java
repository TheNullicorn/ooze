package me.nullicorn.ooze.api.world;

import java.util.Objects;

/**
 * A pair of 2D cartesian coordinates.
 *
 * @author Nullicorn
 */
public class Location2D {

  private final int x;
  private final int z;

  public Location2D(int x, int z) {
    this.x = x;
    this.z = z;
  }

  public int getX() {
    return x;
  }

  public int getZ() {
    return z;
  }

  @Override
  public String toString() {
    return "(" + x + ", " + z + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Location2D that = (Location2D) o;
    return x == that.x &&
        z == that.z;
  }

  @Override
  public int hashCode() {
    return Objects.hash(x, z);
  }
}
