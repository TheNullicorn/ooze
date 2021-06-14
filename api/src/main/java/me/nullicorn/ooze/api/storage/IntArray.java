package me.nullicorn.ooze.api.storage;

/**
 * A data structure that stores an array of positive integers, with a maximum of up to {@link
 * Integer#MAX_VALUE}.
 *
 * @author Nullicorn
 */
public interface IntArray {

  /**
   * @return The value at that {@code index} in the array, or {@code 0} if none has been set.
   * @throws ArrayIndexOutOfBoundsException If the {@code index} is greater than or equal to the
   *                                        value returned by {@link #size()}, or if the {@code
   *                                        index} is a negative number.
   */
  int get(int index);

  /**
   * Stores an integer {@code value} at a specific {@code index} in the array, replacing whatever
   * value was previously at that index.
   *
   * @return The value that was previously at that index.
   * @throws ArrayIndexOutOfBoundsException If the {@code index} is greater than or equal to the
   *                                        value returned by {@link #size()}, or if the {@code
   *                                        index} is a negative number.
   * @throws IllegalArgumentException       If the {@code value} is a negative number, or if it is
   *                                        greater than the value returned by {@link #maxValue()}.
   */
  int set(int index, int value);

  /**
   * @return The number of elements in the array.
   */
  int size();

  /**
   * @return The highest {@code value} allowed via {@link #set(int, int)}.
   */
  int maxValue();

  /**
   * Performs an {@code action} on each element in the array.
   */
  void forEach(DataConsumer action);

  interface DataConsumer {

    /**
     * Performs an action on the data {@code value} at the given {@code index}.
     */
    void accept(int index, int value);
  }
}
