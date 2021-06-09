package me.nullicorn.ooze.storage;

import java.util.Arrays;
import java.util.Objects;
import me.nullicorn.ooze.BitUtils;

/**
 * A compact format for storing many integers with a known limit. Used by Minecraft to store block
 * states.
 * <p>
 * The raw format used to store values is documented <a href=https://wiki.vg/Chunk_Format#Compacted_data_array>here</a>,
 * including a description of the "legacy"/old format mentioned in {@link #toRaw(boolean) toRaw()}
 * and {@link #fromRaw(long[], int, int, boolean) fromRaw()}.
 *
 * @author Nullicorn
 */
public class WordedIntArray implements IntArray {

  private static final int BITS_PER_WORD = Long.SIZE;

  /**
   * Creates a worded array with the same contents, {@link IntArray#size() size} and {@link
   * IntArray#maxValue() maximum value} as the {@code source} array.
   */
  public static WordedIntArray fromIntArray(IntArray source) {
    if (source instanceof WordedIntArray) {
      return (WordedIntArray) source;
    }

    WordedIntArray newArr = new WordedIntArray(source.size(), source.maxValue());
    source.forEach(newArr::set);
    return newArr;
  }

  /**
   * Reads a worded array of integers from its {@link #toRaw(boolean) raw format}.
   *
   * @param size     The number of compact elements in the source array; usually larger than the
   *                 array's actual length.
   * @param isLegacy Whether or not the {@code source} uses the old encoding, where a single value
   *                 in the array could be split across consecutive longs.
   * @param maxValue The highest value that can be stored at any index in the compact array.
   * @see #toRaw(boolean)
   */
  public static WordedIntArray fromRaw(long[] source, int size, int maxValue, boolean isLegacy) {
    if (!isLegacy) {
      // Data should already be formatted properly.
      return new WordedIntArray(size, maxValue, source);
    } else {
      WordedIntArray array = new WordedIntArray(size, maxValue);

      // Extract values from compact format.
      int bitsPerCell = BitUtils.bitsNeededToStore(maxValue);
      int cellMask = BitUtils.createBitMask(bitsPerCell);
      for (int cellIndex = 0; cellIndex < size; cellIndex++) {
        int bitIndex = cellIndex * bitsPerCell;
        int startWord = bitIndex / BITS_PER_WORD;
        int endWord = (bitIndex + bitsPerCell) / BITS_PER_WORD;
        int startOffset = bitIndex % BITS_PER_WORD;

        int value;
        if (startWord == endWord) {
          value = (int) (source[startWord] >> startOffset);
        } else {
          int endOffset = BITS_PER_WORD - startOffset;
          value = (int) (source[startWord] >> startOffset | source[endWord] << endOffset);
        }

        array.set(cellIndex, value & cellMask);
      }

      return array;
    }
  }

  /**
   * @return The number of words needed to store {@code size} values that can be at most {@code
   * maxValue}.
   */
  private static int wordsNeeded(int size, int maxValue) {
    int bitsPerCell = Math.max(4, BitUtils.bitsNeededToStore(maxValue));
    int cellsPerWord = BITS_PER_WORD / bitsPerCell;
    return (int) Math.ceil(size / (double) cellsPerWord);
  }

  // Internal storage for compact values. Each "word" contains multiple "cells" with values.
  // The concept of "words" is borrowed from BitSet.
  private final long[] words;

  // Size in cells, not words.
  private final int size;

  /**
   * The highest value that can be stored at any index in the array.
   */
  /*
   * This is not a technical limit, but is used to calculate the bitmask for individual cells.
   * Because it is user-facing though, incoming values via #set() should be checked against this to
   * avoid confusion.
   */
  private final int maxValue;

  // The number of bits used to store individual values inside their words.
  private final int bitsPerCell;

  // The maximum number of values that can be stored in a single word.
  private final int cellsPerWord;

  // A bitmask with the least significant <bitsPerCell> bits set.
  private final long cellMask;

  public WordedIntArray(int size, int maxValue) {
    this(size, maxValue, new long[wordsNeeded(size, maxValue)]);
  }

  private WordedIntArray(int size, int maxValue, long[] words) {
    if (size < 0) {
      throw new IllegalArgumentException("Illegal array size: " + size);
    } else if (maxValue < 0) {
      throw new IllegalArgumentException("Cannot store signed values");
    }

    this.size = size;
    this.maxValue = maxValue;
    this.words = words;

    bitsPerCell = Math.max(4, BitUtils.bitsNeededToStore(maxValue));
    cellsPerWord = BITS_PER_WORD / bitsPerCell;
    cellMask = BitUtils.createBitMask(bitsPerCell);

    if (words.length < wordsNeeded(size, maxValue)) {
      throw new IllegalArgumentException("Cannot store " + size + " values in " +
                                         words.length + " words");
    }
  }

  @Override
  public int get(int index) {
    if (index < 0 || index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    }

    long word = words[getWordIndexForCell(index)];
    int cellOffset = getCellOffset(index);
    return (int) ((word >>> cellOffset) & cellMask);
  }

  @Override
  public int set(int index, int value) {
    if (index < 0 || index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    } else if (value < 0 || value > maxValue) {
      throw new IllegalArgumentException("Cannot store value " + value + " in data array");
    }

    int wordIndex = getWordIndexForCell(index);
    int cellOffset = getCellOffset(index);
    int previousValue = get(index);

    long word = words[wordIndex];
    word &= ~(cellMask << cellOffset); // Clear the cell.
    word |= (value & cellMask) << cellOffset; // Insert the value.
    words[wordIndex] = word;

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
    int cellIndex = 0;
    for (long word : words) {
      for (int i = 0; i < cellsPerWord && cellIndex < size; i++, cellIndex++) {
        action.accept(cellIndex, (int) (word & cellMask));
        word >>>= bitsPerCell;
      }
    }
  }

  /**
   * @return The index of the word that contains the cell at {@code cellIndex}.
   */
  private int getWordIndexForCell(int cellIndex) {
    return cellIndex / cellsPerWord;
  }

  /**
   * @return The bit offset of a cell at {@code cellIndex} inside its word. Offset is calculated
   * from the rightmost bit.
   */
  private int getCellOffset(int cellIndex) {
    return bitsPerCell * (cellIndex % cellsPerWord);
  }

  /**
   * Converts the array to its simplest form, such that it can be reconstructed via {@link
   * #fromRaw(long[], int, int, boolean) fromRaw()}. The format used to store values is documented
   * <a href=https://wiki.vg/Chunk_Format#Compacted_data_array>here</a>.
   *
   * @param useLegacyEncoding Whether or not the returned array should use the old encoding, where a
   *                          single value could be split across consecutive longs.
   * @see #fromRaw(long[], int, int, boolean)
   */
  public long[] toRaw(boolean useLegacyEncoding) {
    if (!useLegacyEncoding) {
      return words;
    } else {
      long[] legacyWords = new long[(int) Math.ceil(size * bitsPerCell / (double) BITS_PER_WORD)];

      forEach(((index, value) -> {
        int bitIndex = index * bitsPerCell;
        int startWord = bitIndex / BITS_PER_WORD;
        int endWord = (bitIndex + bitsPerCell) / BITS_PER_WORD;
        int startOffset = bitIndex % BITS_PER_WORD;

        value &= cellMask;
        if (startWord == endWord) {
          legacyWords[startWord] |= (value << startOffset);
        } else {
          int endOffset = BITS_PER_WORD - startOffset;
          legacyWords[startWord] |= (value << startOffset);
          legacyWords[endWord] |= (value >> endOffset);
        }
      }));

      return legacyWords;
    }
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
    WordedIntArray that = (WordedIntArray) o;
    return size == that.size &&
           maxValue == that.maxValue &&
           Arrays.equals(words, that.words);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(size, maxValue);
    result = 31 * result + Arrays.hashCode(words);
    return result;
  }
}
