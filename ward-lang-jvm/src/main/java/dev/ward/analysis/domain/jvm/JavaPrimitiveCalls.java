package dev.ward.analysis.domain.jvm;

import java.util.Optional;
import java.util.Set;

/**
 * Finds calls on a {@code String} receiver that return a number or a boolean, as {@code
 * value.length()} does, the {@code size} of a collection and the {@code length} of an array. Such a call gives a site named {@link #NAME} whose arguments are the receiver
 * and the arguments of the call (built by {@code JavaFlowSites}), so a taint specification of an injection into text can
 * list it as a sanitizer: a primitive value cannot carry a line break or markup.
 */
final class JavaPrimitiveCalls {
  /** The name of the site of a call whose result is a number or a boolean. */
  static final String NAME = "<primitive-value>";

  private static final String STRING_TYPE = "java.lang.String";
  // charAt is left out: a char can be a line break or a markup character.
  private static final Set<String> NAMES =
      Set.of(
          "length",
          "isEmpty",
          "isBlank",
          "indexOf",
          "lastIndexOf",
          "equals",
          "equalsIgnoreCase",
          "contains",
          "startsWith",
          "endsWith",
          "matches",
          "compareTo",
          "compareToIgnoreCase",
          "hashCode"
      );

  private static final Set<String> COLLECTION_NAMES = Set.of("size", "isEmpty");
  private static final Set<String> COLLECTIONS =
      Set.of(
          "java.util.Collection",
          "java.util.List",
          "java.util.ArrayList",
          "java.util.LinkedList",
          "java.util.Set",
          "java.util.HashSet",
          "java.util.LinkedHashSet",
          "java.util.TreeSet",
          "java.util.Map",
          "java.util.HashMap",
          "java.util.LinkedHashMap",
          "java.util.TreeMap",
          "java.util.Deque",
          "java.util.ArrayDeque",
          "java.util.Queue"
      );
  private static final String ARRAY_SUFFIX = "[]";

  private JavaPrimitiveCalls() {}

  /**
   * Returns whether a call returns a number or a boolean.
   *
   * @param name the called name
   * @param receiverType the resolved type of the receiver
   */
  static boolean returnsPrimitive(final String name, final Optional<String> receiverType) {
    return NAMES.contains(name) && receiverType.filter(STRING_TYPE::equals).isPresent()
        || COLLECTION_NAMES.contains(name) && receiverType.filter(
            COLLECTIONS::contains
        ).isPresent();
  }

  /**
   * Returns whether a member read is the {@code length} of a variable that is declared as an array.
   *
   * @param name the member name
   * @param declared the type of the qualifier as written, or null when it is not a declared name
   */
  static boolean isArrayLength(final String name, final String declared) {
    return "length".equals(name) && declared != null && declared.endsWith(ARRAY_SUFFIX);
  }
}
