package dev.ward.analysis.domain.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * The text form of a graph rule's configuration in its body. Roots and modules are directory names
 * that cannot contain {@code ,} or {@code ;}, so the form is unambiguous. Tables are separated by
 * {@code ;} and their modules by {@code ,}.
 */
public final class GraphRuleBody {
  private static final String TABLE_SEPARATOR = ";";
  private static final String NAME_SEPARATOR = ",";
  private static final int MIN_MODULES = 2;

  private GraphRuleBody() {}

  /** Returns the body of a slice cycle rule with {@code roots}. */
  public static String encodeRoots(final List<String> roots) {
    return String.join(NAME_SEPARATOR, roots);
  }

  /**
   * Reads the roots of a body written by {@link #encodeRoots(List)}.
   *
   * @throws IllegalArgumentException if the body has no root
   */
  public static List<String> decodeRoots(final String body) {
    final List<String> roots = names(body);
    if (roots.isEmpty()) {
      throw new IllegalArgumentException("a cycle rule needs at least one root");
    }
    return roots;
  }

  /** Returns the body of an independence rule with one list of modules per table. */
  public static String encodeTables(final List<List<String>> tables) {
    return tables.stream()
        .map(GraphRuleBody::encodeRoots)
        .reduce((left, right) -> left + TABLE_SEPARATOR + right)
        .orElse("");
  }

  /**
   * Reads the module lists of a body written by {@link #encodeTables(List)}.
   *
   * @throws IllegalArgumentException if a table has fewer than two modules
   */
  public static List<List<String>> decodeTables(final String body) {
    final List<List<String>> tables = new ArrayList<>();
    for (final String text : body.split(TABLE_SEPARATOR, -1)) {
      final List<String> modules = names(text);
      if (modules.size() < MIN_MODULES) {
        throw new IllegalArgumentException("an independence table needs two modules");
      }
      tables.add(modules);
    }
    return List.copyOf(tables);
  }

  private static List<String> names(final String text) {
    return text.isEmpty() ? List.of() : List.of(text.split(NAME_SEPARATOR, -1));
  }
}
