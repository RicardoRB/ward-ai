package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads the value and type of Java literals. */
final class Literals {
  private static final int HEX_RADIX = 16;
  private static final int OCTAL_RADIX = 8;
  private static final int BINARY_RADIX = 2;
  private static final int DECIMAL_RADIX = 10;
  private static final int UNICODE_DIGITS = 4;
  private static final String STRING = "java.lang.String";
  private static final String INTEGER_KIND = "int";
  private static final String LONG_KIND = "long";
  private static final Set<String> INTEGER_LITERALS =
      Set.of("DECIMAL_LITERAL", "HEX_LITERAL", "OCT_LITERAL", "BINARY_LITERAL");
  private static final Map<Character, Character> ESCAPES =
      Map.of('n', '\n', 't', '\t', 'r', '\r', 'b', '\b', 'f', '\f', 's', ' ');

  private Literals() {}

  /** A literal's type and value; the value of {@code null} is empty. */
  record Literal(JType type, Optional<Object> value) {}

  /** Reads the {@code literal} node, or returns empty when it is not understood. */
  static Optional<Literal> of(final SyntaxNode literal) {
    final String text = Syntax.text(literal);
    final String kind = leafKind(literal);
    if (INTEGER_LITERALS.contains(kind)) {
      return integer(kind, text);
    }
    return switch (kind) {
      case "STRING_LITERAL" -> Optional.of(string(text.substring(1, text.length() - 1)));
      case "TEXT_BLOCK" -> Optional.of(new Literal(JType.ClassType.of(STRING), Optional.empty()));
      case "CHAR_LITERAL" -> Optional.of(character(text));
      case "BOOL_LITERAL" -> Optional.of(primitive("boolean", Boolean.valueOf(text)));
      case "NULL_LITERAL" ->
          Optional.of(new Literal(JType.ClassType.of(Types.NULL), Optional.empty()));
      case "FLOAT_LITERAL", "HEX_FLOAT_LITERAL" -> floating(text);
      default -> Optional.empty();
    };
  }

  private static String leafKind(final SyntaxNode node) {
    SyntaxNode current = node;
    while (!current.children().isEmpty()) {
      current = current.children().get(0);
    }
    return current.kind();
  }

  private static Literal primitive(final String name, final Object value) {
    return new Literal(new JType.Primitive(name), Optional.of(value));
  }

  private static Literal string(final String body) {
    return new Literal(JType.ClassType.of(STRING), Optional.of(unescape(body)));
  }

  private static Literal character(final String text) {
    final String body = unescape(text.substring(1, text.length() - 1));
    return new Literal(
        new JType.Primitive("char"),
        body.isEmpty() ? Optional.empty() : Optional.of(body.substring(0, 1)));
  }

  private static int radix(final String kind) {
    return switch (kind) {
      case "HEX_LITERAL" -> HEX_RADIX;
      case "BINARY_LITERAL" -> BINARY_RADIX;
      case "OCT_LITERAL" -> OCTAL_RADIX;
      default -> DECIMAL_RADIX;
    };
  }

  private static Optional<Literal> integer(final String kind, final String raw) {
    String digits = raw.replace("_", "");
    final boolean isLong = digits.endsWith("l") || digits.endsWith("L");
    if (isLong) {
      digits = digits.substring(0, digits.length() - 1);
    }
    final int radix = radix(kind);
    if ("HEX_LITERAL".equals(kind) || "BINARY_LITERAL".equals(kind)) {
      digits = digits.substring(2);
    } else if ("OCT_LITERAL".equals(kind)) {
      digits = digits.substring(1);
    }
    final String name = isLong ? LONG_KIND : INTEGER_KIND;
    try {
      final long value = Long.parseUnsignedLong(digits.isEmpty() ? "0" : digits, radix);
      return Optional.of(primitive(name, isLong ? value : (long) (int) value));
    } catch (NumberFormatException tooLarge) {
      return Optional.of(new Literal(new JType.Primitive(name), Optional.empty()));
    }
  }

  private static Optional<Literal> floating(final String raw) {
    final String text = raw.replace("_", "");
    final boolean isFloat = text.endsWith("f") || text.endsWith("F");
    final String name = isFloat ? "float" : "double";
    try {
      return Optional.of(primitive(name, Double.parseDouble(text)));
    } catch (NumberFormatException unreadable) {
      return Optional.of(new Literal(new JType.Primitive(name), Optional.empty()));
    }
  }

  private static String unescape(final String body) {
    final StringBuilder text = new StringBuilder();
    int at = 0;
    while (at < body.length()) {
      final char next = body.charAt(at++);
      if (next != '\\' || at >= body.length()) {
        text.append(next);
      } else {
        final char code = body.charAt(at++);
        if (code == 'u') {
          at = unicode(body, at, text);
        } else {
          text.append(ESCAPES.getOrDefault(code, code));
        }
      }
    }
    return text.toString();
  }

  /**
   * Appends the character of a {@code \}{@code uXXXX} escape; {@code at} is after the {@code u}.
   */
  private static int unicode(final String body, final int at, final StringBuilder text) {
    int start = at;
    while (start < body.length() && body.charAt(start) == 'u') {
      start++;
    }
    if (start + UNICODE_DIGITS <= body.length()) {
      try {
        text.append(
            (char) Integer.parseInt(body.substring(start, start + UNICODE_DIGITS), HEX_RADIX));
        return start + UNICODE_DIGITS;
      } catch (NumberFormatException malformed) {
        text.append('u');
      }
    }
    return at;
  }
}
