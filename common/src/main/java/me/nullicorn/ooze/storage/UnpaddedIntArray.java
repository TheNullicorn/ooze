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
 * in-memory and when serialized. Loosely based on Minecraft's block storage format.
 *
 * @author Nullicorn
 */
public class UnpaddedIntArray implements IntArray, OozeSerializable {

  private final byte[] data;

  // Number of "cells" in the array.
  private final int size;

  /*
   * The highest value that can be stored in any cell. This value is not a necessarily a technical
   * limitation, but since it must be provided by the user, we also check against it in #set() to
   * avoid confusion.
   */
  private final int maxValue;

  // The length of each cell in bits.
  private final int bitsPerCell;

  // A mask of [bitsPerCell] set bits.
  private final int cellMask;

  public UnpaddedIntArray(int size, int maxValue) {
    this.size = size;
    this.maxValue = maxValue;
    this.bitsPerCell = BitUtils.bitsNeededToStore(maxValue);
    this.cellMask = BitUtils.createBitMask(bitsPerCell);

    int bytesNeeded = (int) Math.ceil(size * bitsPerCell / (double) Byte.SIZE);
    this.data = new byte[bytesNeeded];
  }

  private UnpaddedIntArray(int size, int maxValue, int bitsPerCell, int cellMask, byte[] data) {
    this.size = size;
    this.maxValue = maxValue;
    this.bitsPerCell = bitsPerCell;
    this.cellMask = cellMask;
    this.data = data;
  }

  @Override
  public int get(int index) {
    if (index < 0 || index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    }

    int bitIndex = index * bitsPerCell;
    int bitOffset = bitIndex % Byte.SIZE;
    int byteIndex = bitIndex / Byte.SIZE;
    int totalBitsRead = 0;

    int value = 0;
    int valueMask = cellMask;

    while (valueMask != 0) {
      value |= (((data[byteIndex] & 0xFF) >> bitOffset) & valueMask) << totalBitsRead;

      int bitsRead = Math.min(Integer.bitCount(valueMask), Byte.SIZE - bitOffset);
      valueMask >>>= bitsRead;

      totalBitsRead += bitsRead;
      byteIndex++;
      bitOffset = 0;
    }

    return value;
  }

  @Override
  public int set(int index, int value) {
    if (index < 0 || index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    } else if (value < 0) {
      throw new IllegalArgumentException("Value is not a positive integer: " + value);
    } else if (value > maxValue) {
      throw new IllegalArgumentException("Value is not <= " + maxValue + ": " + value);
    }

    int bitIndex = index * bitsPerCell;
    int bitOffset = bitIndex % Byte.SIZE;
    int byteIndex = bitIndex / Byte.SIZE;
    int totalBitsWritten = 0;

    int previousValue = 0;
    int valueMask = cellMask;

    while (valueMask != 0) {
      // Read previous value.
      previousValue |= (((data[byteIndex] & 0xFF) >> bitOffset) & valueMask) << totalBitsWritten;

      data[byteIndex] &= ~(valueMask << bitOffset); // Clear all bits in the cell.
      data[byteIndex] |= ((value & valueMask) << bitOffset); // Insert new value into the cell.

      int bitsWritten = Math.min(Integer.bitCount(valueMask), Byte.SIZE - bitOffset);
      value >>>= bitsWritten;
      valueMask >>>= bitsWritten;

      totalBitsWritten += bitsWritten;
      byteIndex++;
      bitOffset = 0;
    }

    return previousValue;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public int maxValue() {
    return maxValue;
  }

  @Override
  public void forEach(DataConsumer action) {
    for (int cellIndex = 0; cellIndex < size; cellIndex++) {
      action.accept(cellIndex, get(cellIndex));
    }
  }

  @Override
  public void serialize(OozeDataOutputStream out) throws IOException {
    out.writeVarInt(maxValue);
    out.writeVarInt(size);
    out.writeVarInt(data.length);
    out.write(data);
  }

  /**
   * Determines whether or not the array must be resized in order to store values up to {@code
   * newMaxValue}. If resizing is not required, the current instance is returned. Otherwise, a new
   * instance is created that can store the new max value. This new instance may or may be backed by
   * the same data source as the original array, in which case modification of either may changed
   * values in both.
   */
  UnpaddedIntArray resizeIfNecessary(int newMaxValue) {
    int requiredCellSize = BitUtils.bitsNeededToStore(newMaxValue);
    if (requiredCellSize == bitsPerCell) {
      if (newMaxValue != maxValue) {
        return new UnpaddedIntArray(size,
            Math.max(maxValue, newMaxValue),
            bitsPerCell,
            cellMask,
            data);
      }
    } else if (requiredCellSize > bitsPerCell) {
      UnpaddedIntArray resized = new UnpaddedIntArray(size, newMaxValue);
      forEach(resized::set);
      return resized;
    }
    return this;
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
    UnpaddedIntArray that = (UnpaddedIntArray) o;
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
