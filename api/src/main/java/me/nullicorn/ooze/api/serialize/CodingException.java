package me.nullicorn.ooze.api.serialize;

import java.io.IOException;

/**
 * Indicates any issue in a {@link Codec} when {@link Codec#encode(Object) encoding} or {@link
 * Codec#decode(Object) decoding}.
 *
 * @author Nullicorn
 * @see Codec
 */
public class CodingException extends IOException {

  public CodingException() {
  }

  public CodingException(String message) {
    super(message);
  }

  public CodingException(String message, Throwable cause) {
    super(message, cause);
  }

  public CodingException(Throwable cause) {
    super(cause);
  }
}
