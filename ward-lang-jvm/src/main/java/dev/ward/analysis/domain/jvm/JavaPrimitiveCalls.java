package dev.ward.analysis.domain.jvm;

import java.util.Optional;
import java.util.Set;

/**
 * Finds calls on a {@code String} receiver that return a number or a boolean, as {@code
 * value.length()} does. Such a call gives a site named {@link #NAME} whose arguments are the receiver
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

  private JavaPrimitiveCalls() {}

  /**
   * Returns whether a call returns a number or a boolean.
   *
   * @param name the called name
   * @param receiverType the resolved type of the receiver
   */
  static boolean returnsPrimitive(final String name, final Optional<String> receiverType) {
    return NAMES.contains(name) && receiverType.filter(STRING_TYPE::equals).isPresent();
  }
}
