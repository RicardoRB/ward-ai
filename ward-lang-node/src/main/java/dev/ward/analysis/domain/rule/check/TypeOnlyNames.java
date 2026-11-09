package dev.ward.analysis.domain.rule.check;

import java.util.List;

/** Formats the names of type-only imports and exports for the messages of the type-only checks. */
final class TypeOnlyNames {

  private static final int SINGLE = 1;

  private TypeOnlyNames() {}

  /** Quotes the names and joins them the way typescript-eslint does: "A", "B" and "C". */
  static String quoted(final List<String> names) {
    final List<String> quoted = names.stream().map(name -> "\"" + name + "\"").toList();
    if (quoted.size() == SINGLE) {
      return quoted.get(0);
    }
    final String head = String.join(", ", quoted.subList(0, quoted.size() - 1));
    return head + " and " + quoted.get(quoted.size() - 1);
  }
}
