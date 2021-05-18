package me.nullicorn.ooze;

/**
 * Helper methods for various binary operations.
 *
 * @author Nullicorn
 */
public final class BitUtils {

  /**
   * @return The minimum number of bits needed to represent the {@code value}.
   */
  public static int bitsNeededToStore(int value) {
    return Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(value));
  }

  /**
   * @return A bitmask with the first {@code width} least significant bits set.
   */
  public static int createBitMask(int width) {
    return (1 << width) - 1;
  }

  /**
   * @return The number of bytes needed to store {@code bitCount} bits.
   */
  public static int bitsToBytes(int bitCount) {
    return (int) Math.ceil(bitCount / (double) Byte.SIZE);
  }

  private BitUtils() {
    throw new UnsupportedOperationException("BitUtils should not be instantiated");
  }
}
