package dev.ward.analysis.domain.rule;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One architecture boundary: a file in one of the {@code from} layers must not depend on a file in
 * one of the {@code forbidden} layers. A file is in a layer when a directory in its path has the
 * layer's name.
 *
 * @param from the source layer names, never empty
 * @param forbidden the layer names the source layers must not depend on, never empty
 */
public record LayerBoundary(List<String> from, List<String> forbidden) {

  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]+");

  /**
   * Copies both lists and checks the names.
   *
   * @throws NullPointerException if a list is missing
   * @throws IllegalArgumentException if a list is empty or a name is not a plain directory name
   */
  public LayerBoundary {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(forbidden, "forbidden");
    from = List.copyOf(from);
    forbidden = List.copyOf(forbidden);
    requireNames(from, "from");
    requireNames(forbidden, "forbidden");
  }

  private static void requireNames(final List<String> names, final String key) {
    if (names.isEmpty()) {
      throw new IllegalArgumentException("'" + key + "' must name at least one layer");
    }
    for (final String name : names) {
      if (!NAME.matcher(name).matches() || ".".equals(name) || "..".equals(name)) {
        throw new IllegalArgumentException(
            "'" + key + "' names '" + name + "', which is not a plain directory name");
      }
    }
  }
}
