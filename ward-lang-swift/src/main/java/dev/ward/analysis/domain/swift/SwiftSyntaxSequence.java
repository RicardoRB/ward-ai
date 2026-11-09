package dev.ward.analysis.domain.swift;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The syntax fallback of {@link SwiftTypesafeArrayInitCheck}: whether a receiver written as text is
 * a sequence, when the type facts do not answer.
 */
final class SwiftSyntaxSequence {

  private static final Pattern ARRAY_LITERAL = Pattern.compile("^\\[.*\\]$");
  private static final Pattern CONSTRUCTOR = Pattern.compile("^(\\w+)\\(\\)$");
  private static final Pattern NAME = Pattern.compile("^\\w+$");
  private static final String SEQUENCE_TYPES = "Sequence|Collection";

  private SwiftSyntaxSequence() {}

  /**
   * Whether {@code receiver} (compact text) of a unit with the compact text {@code file} is a
   * sequence.
   */
  static boolean holds(final String receiver, final String file) {
    if (ARRAY_LITERAL.matcher(receiver).matches()) {
      return true;
    }
    final Matcher constructor = CONSTRUCTOR.matcher(receiver);
    if (constructor.matches()) {
      return file.matches(
          ".*(?:struct|class|enum)" + constructor.group(1) + ":[^{]*(?:" + SEQUENCE_TYPES + ").*");
    }
    return NAME.matcher(receiver).matches() && hasSequenceType(receiver, file);
  }

  /**
   * A name declared with an array or set type, or with a generic type constrained to a sequence.
   */
  private static boolean hasSequenceType(final String name, final String file) {
    if (file.matches(".*\\b" + name + ":(?:\\[|Array<|Set<).*")) {
      return true;
    }
    final Matcher generic = Pattern.compile("\\b" + name + ":(\\w+)\\b").matcher(file);
    while (generic.find()) {
      final String type = generic.group(1);
      if (file.matches(".*<[^>]*\\b" + type + ":[^>,]*(?:" + SEQUENCE_TYPES + ").*")) {
        return true;
      }
    }
    return false;
  }
}
