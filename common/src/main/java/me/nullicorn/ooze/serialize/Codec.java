package me.nullicorn.ooze.serialize;

/**
 * A two-way function for converting between types of objects.
 * <p><br>
 * If encoding or decoding of an object fails, a {@link CodingException} is thrown from the
 * respective method.
 *
 * @param <I> The higher-level type. If the codec performs serialization for example, this would be
 *            the <strong>deserialized</strong> class.
 * @param <O> The lower-level type. When converting objects to and from NBT, for example, this would
 *            be the NBT class.
 * @author Nullicorn
 */
public interface Codec<I, O> {

  O encode(I value) throws CodingException;

  I decode(O value) throws CodingException;
}
