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

    for (int i = 0; i < nextAvailableIndex; i++) {
      if (oldIds[i] != newIds[i]) {
        noChanges = false;
        break;
      }
    }

    locked = true;
    return this;
  }

  /**
   * @return The new ID for the block state that the {@code oldId} refers to. If the ID for that
   * state has not changed, the {@code oldId} is returned as-is.
   */
  public int upgrade(int oldId) {
    if (!locked) {
      throw new IllegalArgumentException("Upgrader must be locked before upgrading.");
    }

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
   */
  public void upgrade(UnpaddedIntArray array) {
    if (!locked) {
      throw new IllegalArgumentException("Upgrader must be locked before upgrading.");
    }

    if (noChanges) {
      return;
    }

    // Determine the highest ID that can be produced by this upgrader.
    int highestId = -1;
    for (int i = 0; i < nextAvailableIndex; i++) {
      int stateId = newIds[i];
      if (stateId > highestId) {
        highestId = stateId;
      }
    }

    int currentMax = array.maxValue();
    if (highestId > currentMax) {
      // Increase the array's size beforehand so it can hold the new highest ID.
      array.setMaxValue(highestId);
    }

    array.forEach(((index, value) -> array.set(index, upgrade(value))));

    if (upgrade(currentMax) < currentMax) {
      // Downsize the array to remove unnecessary extra range.
      // Won't happen if the array was already scaled up before the conversion.
      array.setMaxValue(highestId);
    }
  }

  /**
   * @return The index in the {@link #oldIds old ID array} where {@code oldId} appears, or {@code
   * -1} if the array does not contain that value.
   */
  private int indexOfOld(int oldId) {
    for (int i = 0; i < nextAvailableIndex; i++) {
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
      // Do nothing.
      return oldId;
    }

    @Override
    public void upgrade(UnpaddedIntArray array) {
      // Do nothing.
    }
  }
}
