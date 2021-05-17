package me.nullicorn.ooze.storage;

import java.util.Arrays;

/**
 * A tool for upgrading data that depends on a {@link BlockPalette palette} to hold its state.
 *
 * @author Nullicorn
 */
public class PaletteUpgrader {

  // An upgrader that doesn't do anything to the data sent through it.
  static final PaletteUpgrader dummy = new DummyPaletteUpgrader();

  // Both arrays have the same length; corresponding IDs are found at the same index in each.
  private final int[] oldIds;
  private final int[] newIds;

  // The highest index in the above arrays that has a registered value.
  private int nextAvailableIndex;

  // Whether or not further changes can be registered.
  private boolean locked = false;

  // True only if both arrays are identical in value. Used as a shortcut when upgrading.
  private boolean noChanges = true;

  public PaletteUpgrader(int size) {
    if (size < 0) {
      throw new IllegalArgumentException(
          "Palette upgrader cannot contain a negative number of entries: " + size);
    }

    nextAvailableIndex = 0;
    oldIds = new int[size];
    newIds = new int[size];
    Arrays.fill(oldIds, -1);
    Arrays.fill(newIds, -1);
  }

  /**
   * Registers a change in a block state's palette ID, so that any instances of {@code oldId} passed
   * through {@link #upgrade(int)} or {@link #upgrade(UnpaddedIntArray)} will be replaced with
   * {@code newId}.
   * <p>
   * This cannot be called after an upgrader has been {@link #lock() locked}.
   *
   * @return The current upgrader instance, allowing for chained calls to this method.
   * @throws IllegalStateException If the upgrader has been {@link #lock() locked}.
   */
  public PaletteUpgrader registerChange(int oldId, int newId) {
    if (locked) {
      throw new IllegalStateException("Cannot modify upgrader after finalization");
    }

    // Check that both IDs are in bounds.
    if (oldId < 0) {
      throw new IndexOutOfBoundsException("Invalid palette state ID: " + oldId);
    } else if (newId < 0) {
      throw new IndexOutOfBoundsException("Invalid palette state ID: " + newId);
    }

    int index = nextAvailableIndex++;
    oldIds[index] = oldId;
    newIds[index] = newId;
    return this;
  }

  /**
   * Disallows future ID changes from being registered.
   *
   * @throws IllegalStateException If this method is called more than once on an instance.
   * @see #registerChange(int, int)
   */
  public PaletteUpgrader lock() {
    if (locked) {
      throw new IllegalStateException("Already locked");
    }

    noChanges = Arrays.equals(oldIds, newIds);
    locked = true;
    return this;
  }

  /**
   * @return The new ID for the block state that the {@code oldId} refers to. If the ID for that
   * state has not changed, the {@code oldId} is returned as-is.
   */
  public int upgrade(int oldId) {
    if (noChanges) {
      return oldId;
    }

    int stateIndex = indexOfOld(oldId);

    if (stateIndex < 0) {
      return oldId;
    }
    return newIds[stateIndex];
  }

  /**
   * Upgrades an {@code array} of state IDs so that any outdated IDs in the array are upgraded to
   * their new values, while unchanged IDs remain the same.
   * <p>
   * <strong>WARNING:</strong> This method may cause the original array to be resized, which can
   * potentially mess up existing instances. Any references to the original array should be updated
   * to the returned one.
   *
   * @return An array of state IDs that respect the changes made by the upgrader. The {@link
   * UnpaddedIntArray#size() size} of this array is the same as the input array, but the {@link
   * UnpaddedIntArray#maxValue() maximum value} may have changed. See the warning above.
   */
  public UnpaddedIntArray upgrade(UnpaddedIntArray array) {
    if (noChanges) {
      return array;
    }

    int highestId = -1;
    for (int stateId : newIds) {
      if (stateId > highestId) {
        highestId = stateId;
      }
    }

    if (highestId < 0) {
      throw new IllegalStateException("Missing upgrade data");
    }

    UnpaddedIntArray upgraded = array.resizeIfNecessary(highestId);
    upgraded.forEach(((index, value) -> upgraded.set(index, upgrade(value))));
    return upgraded;
  }

  /**
   * @return The index in the {@link #oldIds old ID array} where {@code oldId} appears, or {@code
   * -1} if the array does not contain that value.
   */
  private int indexOfOld(int oldId) {
    for (int i = 0; i < oldIds.length; i++) {
      if (oldIds[i] == oldId) {
        return i;
      }
    }
    return -1;
  }

  /**
   * A palette upgrader that does not modify any data put through it.
   *
   * @author Nullicorn
   */
  private static final class DummyPaletteUpgrader extends PaletteUpgrader {

    DummyPaletteUpgrader() {
      super(0);
      lock();
    }

    @Override
    public int upgrade(int oldId) {
      return oldId;
    }

    @Override
    public UnpaddedIntArray upgrade(UnpaddedIntArray array) {
      return array;
    }
  }
}
