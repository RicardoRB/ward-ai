package dev.ward.analysis.frontend.antlr.format.csharp;

import java.util.EnumSet;
import java.util.Set;

/**
 * Decides the space between two tokens on one line from their text and tree roles. A rule is a
 * token pair, so a rewrite never changes a token: only the space between tokens.
 *
 * @param prev the text of the token before the gap
 * @param prevRole the role of that token
 * @param next the text of the token after the gap
 * @param nextRole the role of that token
 */
record CSharpSpacing(String prev, CSharpRole prevRole, String next, CSharpRole nextRole) {
  /** Whether the source space between two tokens becomes a space, none, or stays as written. */
  enum Kind {
    SPACE,
    NONE,
    KEEP
  }

  private static final Set<CSharpRole> NONE_BEFORE_ROLES =
      EnumSet.of(
          CSharpRole.GENERIC_OPEN,
          CSharpRole.GENERIC_CLOSE,
          CSharpRole.NULLABLE,
          CSharpRole.NULL_CONDITIONAL,
          CSharpRole.POSTFIX
      );
  private static final Set<CSharpRole> NONE_AFTER_ROLES =
      EnumSet.of(
          CSharpRole.GENERIC_OPEN,
          CSharpRole.PREFIX,
          CSharpRole.CAST_CLOSE,
          CSharpRole.NULL_CONDITIONAL
      );
  private static final Set<CSharpRole> CLOSING_ROLES =
      EnumSet.of(CSharpRole.GENERIC_CLOSE, CSharpRole.NULLABLE);
  private static final Set<String> NONE_BEFORE =
      Set.of(",", ";", ")", "]", ".", "::", "->", "..", ":");
  private static final Set<String> NONE_AFTER = Set.of("(", "[", ".", "::", "->", "..");
  private static final Set<String> NONE_AFTER_CLOSER = Set.of(
      "(",
      "[",
      ")",
      "]",
      ",",
      ";",
      ".",
      ">"
  );
  private static final Set<String> BINARY =
      Set.of(
          "=",
          "==",
          "!=",
          "<=",
          ">=",
          "&&",
          "||",
          "??",
          "??=",
          "=>",
          "+=",
          "-=",
          "*=",
          "/=",
          "%=",
          "&=",
          "|=",
          "^=",
          "<<="
      );
  private static final Set<String> KEYWORDS_BEFORE_PAREN =
      Set.of(
          "if",
          "for",
          "foreach",
          "while",
          "switch",
          "catch",
          "using",
          "lock",
          "fixed",
          "when",
          "return",
          "throw",
          "await",
          "in",
          "is",
          "and",
          "or",
          "not",
          "case",
          "select",
          "group",
          "join",
          "let",
          "from",
          "where",
          "orderby",
          "static",
          "var",
          "async",
          "ref",
          "out",
          "params"
      );

  /**
   * Decides the spacing of the gap.
   *
   * @return the spacing
   */
  Kind spacing() {
    if (noSpaceBefore() || noSpaceAfter()) {
      return Kind.NONE;
    }
    if (spaceRequired()) {
      return Kind.SPACE;
    }
    return callOrIndex() ? Kind.NONE : Kind.KEEP;
  }

  private boolean noSpaceBefore() {
    final boolean colon = ":".equals(next) && nextRole == CSharpRole.NONE;
    final boolean listed =
        NONE_BEFORE.contains(next) && !":".equals(next) && nextRole != CSharpRole.SPREAD;
    return colon || listed || NONE_BEFORE_ROLES.contains(nextRole);
  }

  private boolean noSpaceAfter() {
    final boolean angle = NONE_AFTER_ROLES.contains(prevRole);
    final boolean closer = CLOSING_ROLES.contains(prevRole) && NONE_AFTER_CLOSER.contains(next);
    final boolean spread = prevRole == CSharpRole.SPREAD && !isWord(next);
    final boolean operator = prevRole == CSharpRole.OPERATOR_NAME && "(".equals(next);
    final boolean listed = NONE_AFTER.contains(prev) && prevRole != CSharpRole.SPREAD;
    return angle || closer || spread || operator || listed;
  }

  private boolean spaceRequired() {
    return operatorSpace() || braceSpace() || wordSpace() || separatorSpace();
  }

  private boolean separatorSpace() {
    final boolean separator = ",".equals(prev) || ";".equals(prev) || ":".equals(prev);
    return separator || KEYWORDS_BEFORE_PAREN.contains(prev) && "(".equals(next);
  }

  private boolean operatorSpace() {
    if (prevRole == CSharpRole.OPERATOR_NAME || nextRole == CSharpRole.OPERATOR_NAME) {
      return nextRole == CSharpRole.OPERATOR_NAME;
    }
    return prevRole == CSharpRole.BINARY
        || nextRole == CSharpRole.BINARY
        || prevRole == CSharpRole.COLON_SPACED
        || nextRole == CSharpRole.COLON_SPACED
        || BINARY.contains(prev)
        || BINARY.contains(next);
  }

  private boolean braceSpace() {
    return "{".equals(prev) || "}".equals(next) || "}".equals(prev) || "{".equals(next);
  }

  private boolean wordSpace() {
    final boolean closer =
        prevRole == CSharpRole.GENERIC_CLOSE
            || prevRole == CSharpRole.NULLABLE
            || ")".equals(prev)
            || "]".equals(prev);
    final boolean spread = prevRole == CSharpRole.SPREAD && isWord(next);
    return isWord(prev) && isWord(next) || closer && isWord(next) || spread;
  }

  private boolean callOrIndex() {
    final boolean opener = "(".equals(next) || "[".equals(next);
    final boolean callee =
        isWord(prev) || ")".equals(prev) || "]".equals(prev) && "[".equals(next)
            || prevRole == CSharpRole.GENERIC_CLOSE;
    return opener && callee;
  }

  private static boolean isWord(final String text) {
    final char first = text.charAt(0);
    return Character.isLetterOrDigit(first) || "_@\"'$".indexOf(first) >= 0;
  }
}
