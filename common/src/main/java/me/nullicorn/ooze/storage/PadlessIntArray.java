package me.nullicorn.ooze.storage;

import java.io.IOException;
import me.nullicorn.ooze.serialize.IntArray;
import me.nullicorn.ooze.serialize.OozeDataOutputStream;
import me.nullicorn.ooze.serialize.OozeSerializable;

/**
 * An integer array that internally packs values as close as possible to maintain low memory and
 * disk space when serialized. Loosely based on Minecraft's block storage format.
 *
 * @author Nullicorn
 */
public class PadlessIntArray implements IntArray, OozeSerializable {

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

  public PadlessIntArray(int size, int maxValue) {
    this.size = size;
    this.maxValue = maxValue;
    this.bitsPerCell = Integer.SIZE - Integer.numberOfLeadingZeros(maxValue);
    this.cellMask = (1 << bitsPerCell) - 1;

    int bytesNeeded = (int) Math.ceil(size * bitsPerCell / (double) Byte.SIZE);
    this.data = new byte[bytesNeeded];
  }

  @Override
  public int get(int index) {
    if (index < 0 || index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    }

    int value = 0;
    int bitIndex = index * bitsPerCell;
    int bitOffset = bitIndex % Byte.SIZE;
    int byteIndex = bitIndex / Byte.SIZE;
    int valueMask = cellMask;
    int totalBitsRead = 0;

    while (valueMask != 0) {
      value |= (((data[byteIndex] & 0xFF) >> bitOffset) & valueMask) << totalBitsRead;

      int bitsRead = Math.min(Integer.bitCount(valueMask), Byte.SIZE - bitOffset);
      totalBitsRead += bitsRead;
      valueMask >>>= bitsRead;
      byteIndex++;
      bitOffset = 0;
    }

    return value;
  }

  @Override
  public void set(int index, int value) {
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
    int valueMask = cellMask;

    while (valueMask != 0) {
      data[byteIndex] &= ~(valueMask << bitOffset); // Clear all bits in the cell.
      data[byteIndex] |= ((value & valueMask) << bitOffset); // Insert the value into the cell.

      int bitsWritten = Math.min(Integer.bitCount(valueMask), Byte.SIZE - bitOffset);
      value >>>= bitsWritten;
      valueMask >>>= bitsWritten;
      byteIndex++;
      bitOffset = 0;
    }
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
    out.write(data);
  }
}
