package dev.ward.domain.format;

import dev.ward.analysis.domain.format.FormatOptions;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The resolved formatting settings of one file.
 *
 * @param enabled false when {@code ward_format = false} excludes the file
 * @param layout the file layout
 * @param maxBlankLines the most blank lines kept in a row
 * @param options the values of the language's declared options
 */
public record FormatSettings(
    boolean enabled, FormatLayout layout, int maxBlankLines, FormatOptions options) {

  /**
   * Checks the components.
   *
   * @throws NullPointerException if a component is missing
   */
  public FormatSettings {
    Objects.requireNonNull(layout, "layout");
    Objects.requireNonNull(options, "options");
  }

  /**
   * Describes every setting that changes the output, in a fixed order, for the analysis cache key.
   *
   * @return the description
   */
  public String fingerprint() {
    return enabled + "|" + new TreeMap<>(options.values()) + "|" + maxBlankLines + "|" + layout;
  }
}
