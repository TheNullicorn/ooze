package me.nullicorn.ooze.storage;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import me.nullicorn.ooze.BitUtils;
import me.nullicorn.ooze.serialize.IntArray;
import me.nullicorn.ooze.serialize.OozeDataOutputStream;
import me.nullicorn.ooze.serialize.OozeSerializable;

/**
 * An integer array that internally packs values as close as possible to maintain low footprint
 * in-memory and when serialized. Loosely based on {@link WordedIntArray Minecraft's block storage
 * format}.
 * <p>
 * TODO: 5/26/21 Document format.
 *
 * @author Nullicorn
 */
public class BitCompactIntArray implements IntArray, OozeSerializable {

  /**
   * Creates a compact array with the same contents, {@link IntArray#size() size} and {@link
   * IntArray#maxValue() maximum value} as the {@code source} array.
   */
  public static BitCompactIntArray fromIntArray(IntArray source) {
    if (source instanceof BitCompactIntArray) {
      return (BitCompactIntArray) source;
    }

    BitCompactIntArray newArr = new BitCompactIntArray(source.size(), source.maxValue());
    source.forEach(newArr::set);
    return newArr;
  }

  /**
   * Performs the {@link #get(int)} operation on a compact int array independent of its {@link
   * BitCompactIntArray wrapper} object. This allows new arrays to be read directly, such as when
   * resizing.
   */
  private static int getInternal(byte[] raw, int bitsPerCell, int cellMask, int index) {
    int bitIndex = index * bitsPerCell;
    int bitOffset = bitIndex % Byte.SIZE;
    int byteIndex = bitIndex / Byte.SIZE;
    int totalBitsRead = 0;

    int value = 0;
    int valueMask = cellMask;

    while (valueMask != 0) {
      value |= (((raw[byteIndex] & 0xFF) >> bitOffset) & valueMask) << totalBitsRead;

      int bitsRead = Math.min(Integer.bitCount(valueMask), Byte.SIZE - bitOffset);
      valueMask >>>= bitsRead;

      totalBitsRead += bitsRead;
      byteIndex++;
      bitOffset = 0;
    }

    return value;
  }

  /**
   * Performs the {@link #set(int, int)} operation on a compact int array independent of its {@link
   * BitCompactIntArray wrapper} object. This allows new arrays to be modified directly, such as
   * when resizing.
   */
  private static int setInternal(byte[] raw, int bitsPerCell, int cellMask, int index, int value) {
    int bitIndex = index * bitsPerCell;
    int bitOffset = bitIndex % Byte.SIZE;
    int byteIndex = bitIndex / Byte.SIZE;
    int totalBitsWritten = 0;

    int previousValue = 0;
    int valueMask = cellMask;

    while (valueMask != 0) {
      // Read previous value from the cell.
      previousValue |= (((raw[byteIndex] & 0xFF) >> bitOffset) & valueMask) << totalBitsWritten;

      raw[byteIndex] &= ~(valueMask << bitOffset); // Clear all bits in the cell.
      raw[byteIndex] |= ((value & valueMask) << bitOffset); // Insert new value into the cell.

      int bitsWritten = Math.min(Integer.bitCount(valueMask), Byte.SIZE - bitOffset);
      value >>>= bitsWritten;
      valueMask >>>= bitsWritten;

      totalBitsWritten += bitsWritten;
      byteIndex++;
      bitOffset = 0;
    }

    return previousValue;
  }

  private byte[] data;

  // Number of "cells" in the array.
  private final int size;

  /*
   * The highest value that can be stored in any cell. This value is not a necessarily a technical
   * limitation, but since it must be provided by the user, we also check against it in #set() to
   * avoid confusion.
   */
  private int maxValue;

  // The length of each cell in bits.
  private int bitsPerCell;

  // A mask of [bitsPerCell] set bits.
  private int cellMask;

  public BitCompactIntArray(int size, int maxValue) {
    this.size = size;
    this.maxValue = maxValue;
    this.bitsPerCell = BitUtils.bitsNeededToStore(maxValue);
    this.cellMask = BitUtils.createBitMask(bitsPerCell);
    this.data = new byte[BitUtils.bitsToBytes(size * bitsPerCell)];
  }

  @Override
  public int get(int index) {
    if (index < 0 || index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    }

    return getInternal(data, bitsPerCell, cellMask, index);
  }

  @Override
  public int set(int index, int value) {
    if (index < 0 || index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    } else if (value < 0) {
      throw new IllegalArgumentException("Array value is not a positive integer: " + value);
    } else if (value > maxValue) {
      throw new IllegalArgumentException("Array value must be <= " + maxValue + ": " + value);
    }

    return setInternal(data, bitsPerCell, cellMask, index, value);
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public int maxValue() {
    return maxValue;
  }

  /**
   * Changes the maximum allowed value to be inserted into the array. If the {@code newMaxValue} is
   * less than the previous {@link #maxValue() maxValue}, it is expected that all values in the
   * array are already less than the new maximum.
   *
   * @throws IllegalStateException If the array contains values greater than the new maximum value.
   */
  public void setMaxValue(int newMaxValue) {
    if (newMaxValue == maxValue) {
      return;
    } else if (BitUtils.bitsNeededToStore(newMaxValue) > bitsPerCell) {
      // Resize required to store higher values.
      resize(newMaxValue);
    } else {
      // Ensure no existing values are out of bounds.
      for (int i = 0; i < size; i++) {
        int value = get(i);
        if (value > newMaxValue) {
          throw new IllegalStateException("Cannot change maximum value to " + newMaxValue +
                                          " while array contains value " + value +
                                          " at index " + i);
        }
      }
    }
    maxValue = newMaxValue;
  }

  @Override
  public void forEach(DataConsumer action) {
    for (int cellIndex = 0; cellIndex < size; cellIndex++) {
      action.accept(cellIndex, get(cellIndex));
    }
  }

  @Override
  public void serialize(OozeDataOutputStream out) throws IOException {
    if (BitUtils.bitsNeededToStore(maxValue) < bitsPerCell) {
      // Ensure serialized form is as compact as possible.
      resize(maxValue);
    }

    out.write(data);
  }

  /**
   * Changes the size of the internal array so that is only as large as is needed to store values up
   * to {@code newMaxValue}. If this change causes the size to decrease, it is expected that the
   * array does not contain any values higher than the new maximum.
   * <p>
   * This method does not change the array's {@link #maxValue}, which must be done separately if
   * needed.
   *
   * @throws IllegalStateException If the array contains any values larger than {@code
   *                               newMaxValue}.
   */
  private void resize(int newMaxValue) {
    int newBitsPerCell = BitUtils.bitsNeededToStore(newMaxValue);
    int newCellMask = BitUtils.createBitMask(newBitsPerCell);
    if (newBitsPerCell == bitsPerCell) {
      // Resizing wouldn't change anything.
      return;
    }

    byte[] newData = new byte[BitUtils.bitsToBytes(size * newBitsPerCell)];
    for (int i = 0; i < size; i++) {
      int value = get(i);
      if (value > newMaxValue) {
        throw new IllegalStateException("Source array contains values larger than new maximum (" +
                                        newMaxValue + "): " + value);
      }
      setInternal(newData, newBitsPerCell, newCellMask, i, value);
    }

    data = newData;
    bitsPerCell = newBitsPerCell;
    cellMask = newCellMask;
  }

  @Override
  public String toString() {
    if (size == 0) {
      return "[]";
    }

    StringBuilder b = new StringBuilder();
    b.append('[');

    forEach((index, value) -> {
      b.append(value);
      if (index < size - 1) {
        b.append(", ");
      }
    });

    b.append(']');
    return b.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BitCompactIntArray that = (BitCompactIntArray) o;
    return size == that.size &&
           maxValue == that.maxValue &&
           Arrays.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(size, maxValue);
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }
}
