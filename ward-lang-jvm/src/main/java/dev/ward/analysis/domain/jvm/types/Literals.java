package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;

/** Reads the value and type of Java literals. */
final class Literals {
  private static final int HEX_RADIX = 16;
  private static final int OCTAL_RADIX = 8;
  private static final int BINARY_RADIX = 2;
  private static final String STRING = "java.lang.String";

  private Literals() {}

  /** A literal's type and value; the value of {@code null} is empty. */
  record Literal(JType type, Optional<Object> value) {}

  /** Reads the {@code literal} node, or returns empty when it is not understood. */
  static Optional<Literal> of(final SyntaxNode literal) {
    final String text = Syntax.text(literal);
    final String kind = literal.children().isEmpty() ? literal.kind() : leafKind(literal);
    return switch (kind) {
      case "STRING_LITERAL" -> Optional.of(string(text.substring(1, text.length() - 1)));
      case "TEXT_BLOCK" -> Optional.of(new Literal(JType.ClassType.of(STRING), Optional.empty()));
      case "CHAR_LITERAL" -> Optional.of(character(text));
      case "BOOL_LITERAL" -> Optional.of(primitive("boolean", Boolean.valueOf(text)));
      case "NULL_LITERAL" -> Optional.of(new Literal(JType.ClassType.of("<null>"), Optional.empty()));
      case "DECIMAL_LITERAL", "HEX_LITERAL", "OCT_LITERAL", "BINARY_LITERAL" -> integer(kind, text);
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

  private static Optional<Literal> integer(final String kind, final String raw) {
    String digits = raw.replace("_", "");
    final boolean isLong = digits.endsWith("l") || digits.endsWith("L");
    if (isLong) {
      digits = digits.substring(0, digits.length() - 1);
    }
    final int radix =
        switch (kind) {
          case "HEX_LITERAL" -> HEX_RADIX;
          case "BINARY_LITERAL" -> BINARY_RADIX;
          case "OCT_LITERAL" -> OCTAL_RADIX;
          default -> 10;
        };
    if (radix != 10) {
      digits = digits.substring("OCT_LITERAL".equals(kind) ? 1 : 2);
    }
    try {
      final long value = Long.parseUnsignedLong(digits.isEmpty() ? "0" : digits, radix);
      final long typed = isLong ? value : (int) value;
      return Optional.of(primitive(isLong ? "long" : "int", typed));
    } catch (NumberFormatException tooLarge) {
      return Optional.of(new Literal(new JType.Primitive(isLong ? "long" : "int"), Optional.empty()));
    }
  }

  private static Optional<Literal> floating(final String raw) {
    final String text = raw.replace("_", "");
    final boolean isFloat = text.endsWith("f") || text.endsWith("F");
    try {
      final double value = Double.parseDouble(text);
      return Optional.of(primitive(isFloat ? "float" : "double", value));
    } catch (NumberFormatException unreadable) {
      return Optional.of(new Literal(new JType.Primitive(isFloat ? "float" : "double"), Optional.empty()));
    }
  }

  private static String unescape(final String body) {
    final StringBuilder text = new StringBuilder();
    for (int at = 0; at < body.length(); at++) {
      final char next = body.charAt(at);
      if (next != '\\' || at + 1 >= body.length()) {
        text.append(next);
        continue;
      }
      final char code = body.charAt(++at);
      switch (code) {
        case 'n' -> text.append('\n');
        case 't' -> text.append('\t');
        case 'r' -> text.append('\r');
        case 'b' -> text.append('\b');
        case 'f' -> text.append('\f');
        case 's' -> text.append(' ');
        case 'u' -> at = unicode(body, at, text);
        default -> text.append(code);
      }
    }
    return text.toString();
  }

  private static int unicode(final String body, final int at, final StringBuilder text) {
    int start = at;
    while (start + 1 < body.length() && body.charAt(start + 1) == 'u') {
      start++;
    }
    if (start + 4 < body.length() + 0 && start + 4 <= body.length() - 1 + 1) {
      try {
        text.append((char) Integer.parseInt(body.substring(start + 1, start + 5), HEX_RADIX));
        return start + 4;
      } catch (NumberFormatException | IndexOutOfBoundsException malformed) {
        text.append('u');
      }
    }
    return at;
  }
}
