package dev.ward.infrastructure.format;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One {@code [glob]} section of an {@code .editorconfig} file.
 *
 * @param header the glob text as written, without brackets
 * @param pattern the compiled glob
 * @param properties the keys (lowercase) and values of the section, copied on construction
 */
record EditorConfigSection(String header, Pattern pattern, Map<String, String> properties) {

  /**
   * Copies the properties.
   *
   * @throws NullPointerException if a component is missing
   */
  EditorConfigSection {
    Objects.requireNonNull(header, "header");
    Objects.requireNonNull(pattern, "pattern");
    properties = Map.copyOf(properties);
  }
}
