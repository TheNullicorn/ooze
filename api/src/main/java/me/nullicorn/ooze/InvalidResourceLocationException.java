package me.nullicorn.ooze;

/**
 * Thrown to indicate that a {@link ResourceLocation} was in an invalid format and could not be
 * parsed.
 *
 * @author Nullicorn
 * @see ResourceLocation
 */
public class InvalidResourceLocationException extends Exception {

  public InvalidResourceLocationException() {
  }

  public InvalidResourceLocationException(String message) {
    super(message);
  }

  public InvalidResourceLocationException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidResourceLocationException(Throwable cause) {
    super(cause);
  }
}
