package dev.ward.analysis.domain.openapi.breaking;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Reads a media type name such as {@code application/json;charset=utf-8}. */
final class MediaTypeNames {

  private MediaTypeNames() {}

  static String essence(final String name) {
    final int cut = name.indexOf(';');
    return (cut < 0 ? name : name.substring(0, cut)).trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Tells whether a parameter of {@code before} changed its value, or is gone, in {@code after}.
   */
  static boolean differs(final String before, final String after, final boolean changed) {
    final Map<String, String> kept = parameters(after);
    return parameters(before).entrySet().stream()
        .anyMatch(
            parameter ->
                changed
                    ? kept.containsKey(parameter.getKey())
                        && !kept.get(parameter.getKey()).equals(parameter.getValue())
                    : !kept.containsKey(parameter.getKey()));
  }

  /** The parameters of a media type name, by lower-case name. */
  static Map<String, String> parameters(final String name) {
    final Map<String, String> parameters = new LinkedHashMap<>();
    final String[] parts = name.split(";");
    for (int index = 1; index < parts.length; index++) {
      final String[] pair = parts[index].split("=", 2);
      parameters.put(
          pair[0].trim().toLowerCase(Locale.ROOT), pair.length > 1 ? pair[1].trim() : "");
    }
    return parameters;
  }
}
