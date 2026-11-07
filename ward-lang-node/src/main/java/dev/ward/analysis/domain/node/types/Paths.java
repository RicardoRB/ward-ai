package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.List;

/** Normalizes project-relative paths. */
final class Paths {
  private Paths() {}

  /** Removes {@code .} and {@code ..} segments from a {@code /} separated path. */
  static String normalize(final String path) {
    final List<String> parts = new ArrayList<>();
    for (final String part : path.split("/")) {
      if ("..".equals(part) && !parts.isEmpty()) {
        parts.remove(parts.size() - 1);
      } else if (!".".equals(part) && !part.isEmpty() && !"..".equals(part)) {
        parts.add(part);
      }
    }
    return String.join("/", parts);
  }
}
