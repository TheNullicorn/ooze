package me.nullicorn.ooze;

import java.util.regex.Pattern;
import lombok.Getter;

/**
 * A path identified by its namespace. Used by Minecraft for block and item IDs among other things.
 *
 * @author Nullicorn
 */
public class ResourceLocation {

  // Used when no namespace is provided.
  private static final String DEFAULT_NAMESPACE = "minecraft";

  // Character used to separate namespace from path.
  // Stored as a string to avoid char conversion in String#split().
  private static final String PATH_INDICATOR = ":";

  // Used to validate parsed resource locations.
  private static final Pattern namespaceFormat = Pattern.compile("^[a-z0-9._\\-]+$");
  private static final Pattern pathFormat      = Pattern.compile("^[a-z0-9/._\\-]+$");

  /**
   * Constructs a resource location by parsing the provided <code>value</code>.
   * <ul>
   *   <li>If <code>value</code> is a full resource location, (e.g. "minecraft:stone") then all parts are used.</li>
   *   <li>If <code>value</code> is missing a namespace (e.g. "stone"), then the default namespace "minecraft" is used.</li>
   * </ul>
   *
   * @return The parsed resource location.
   * @throws InvalidResourceLocationException If the <code>value</code> could not be parsed as a
   *                                          resource location.
   */
  public static ResourceLocation fromString(String value) throws InvalidResourceLocationException {
    String namespace;
    String path;

    String[] segments = value.split(PATH_INDICATOR, 3);
    switch (segments.length) {
      case 1:
        namespace = DEFAULT_NAMESPACE;
        path = value;
        break;

      case 2:
        namespace = segments[0];
        path = segments[1];
        break;

      default:
        throw new InvalidResourceLocationException(
            "Not a valid resource location: \"" + value + "\"");
    }

    return new ResourceLocation(namespace, path);
  }

  @Getter
  private final String namespace;

  @Getter
  private final String path;

  public ResourceLocation(String namespace, String path) {
    if (!namespaceFormat.matcher(namespace).matches()) {
      throw new IllegalArgumentException("Invalid namespace: \"" + namespace + "\"");
    } else if (!pathFormat.matcher(path).matches()) {
      throw new IllegalArgumentException("Invalid path: \"" + path + "\"");
    }

    this.namespace = namespace;
    this.path = path;
  }

  @Override
  public String toString() {
    return namespace + PATH_INDICATOR + path;
  }
}
