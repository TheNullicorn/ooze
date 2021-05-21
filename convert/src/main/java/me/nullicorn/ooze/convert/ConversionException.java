package me.nullicorn.ooze.convert;

import java.io.IOException;

/**
 * Indicates a problem while attempting to convert between types or formats.
 *
 * @author Nullicorn
 */
public class ConversionException extends IOException {

  public ConversionException() {
  }

  public ConversionException(String message) {
    super(message);
  }

  public ConversionException(String message, Throwable cause) {
    super(message, cause);
  }

  public ConversionException(Throwable cause) {
    super(cause);
  }
}
