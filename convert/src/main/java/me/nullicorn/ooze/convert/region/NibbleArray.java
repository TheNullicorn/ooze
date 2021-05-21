package me.nullicorn.ooze.convert.region;

import me.nullicorn.ooze.serialize.IntArray;

/**
 * An array of 4-bit integer values.
 *
 * @author Nullicorn
 */
public class NibbleArray implements IntArray {

  private static final int BITS_PER_CELL = 4;
  private static final int MAX_VALUE     = (1 << BITS_PER_CELL) - 1;

  /**
   * Splits a byte array up into {@code size} individual nibbles, 2 per byte.
   *
   * @param size The number of nibbles in the source array.
   * @throws IllegalArgumentException If the source array is not big enough to fit {@code size}
   *                                  nibbles.
   */
  public static NibbleArray fromBytes(byte[] source, int size) {
    if (source.length < bytesNeeded(size)) {
      throw new IllegalArgumentException("Source array cannot fit " + size + "nibbles");
    }
    return new NibbleArray(size, source);
  }

  /**
   * The number of bytes needed to store {@code nibbleCount} nibbles.
   */
  private static int bytesNeeded(int nibbleCount) {
    return (int) Math.ceil((float) nibbleCount / 2);
  }

  private final int    size;
  private final byte[] words;

  public NibbleArray(int size) {
    this(size, new byte[bytesNeeded(size)]);
  }

  private NibbleArray(int size, byte[] words) {
    this.size = size;
    this.words = words;
  }

  @Override
  public int get(int index) {
    if (index < 0 || index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    }
    return (index & 1) == 0
        ? words[index / 2] & 0xF
        : (words[index / 2] >> BITS_PER_CELL) & 0xF;
  }

  @Override
  public int set(int index, int value) {
    if (index < 0 || index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    } else if (value < 0 || value > MAX_VALUE) {
      throw new IllegalArgumentException("Value " + value + " cannot fit in a nibble");
    }

    int previousValue = get(index);
    int wordIndex = index / 2;
    value &= 0xF;
    words[wordIndex] &= ((index & 1) == 0 ? ~0xF : 0xF);
    words[wordIndex] |= ((index & 1) == 0 ? value : value << BITS_PER_CELL);
    return previousValue;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public int maxValue() {
    return MAX_VALUE;
  }

  @Override
  public void forEach(DataConsumer action) {
    for (int i = 0; i < size; i++) {
      action.accept(i, get(i));
    }
  }

  @Override
  public String toString() {
    if (size == 0) {
      return "[]";
    }

    StringBuilder b = new StringBuilder();
    b.append('[');
    for (int i = 0; i < size; i++) {
      b.append(get(i));
      if (i + 1 < size) {
        b.append(", ");
      }
    }
    b.append(']');
    return b.toString();
  }
}
