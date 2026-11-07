package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** The types of literal expressions, and widening of literal types. */
final class Literals {
  static final String NUMBER = "number";
  static final String STRING = "string";
  static final String BOOLEAN = "boolean";
  private static final String BIGINT = "bigint";

  private Literals() {}

  /** Returns the type of a {@code literal} node, or empty if it is not a simple literal. */
  static Optional<Ty> of(final SyntaxNode literal) {
    final List<String> tokens = TreeKit.leaves(literal);
    if (tokens.size() != 1) {
      return Optional.of(Ty.named(STRING)).filter(ignored -> isTemplate(tokens));
    }
    final String token = tokens.get(0);
    return Optional.of(typeOfToken(token, literal));
  }

  private static boolean isTemplate(final List<String> tokens) {
    return !tokens.isEmpty() && tokens.get(0).startsWith("`");
  }

  private static Ty typeOfToken(final String token, final SyntaxNode literal) {
    if (token.startsWith("\"") || token.startsWith("'")) {
      return Ty.named("\"" + TreeKit.unquote(token) + "\"");
    }
    return switch (token) {
      case "true", "false", "null" -> Ty.named(token);
      default -> numeric(token, literal);
    };
  }

  private static Ty numeric(final String token, final SyntaxNode literal) {
    if (token.startsWith("/")) {
      return Ty.named("RegExp");
    }
    if (token.endsWith("n") && !token.startsWith("0x") || "bigintLiteral".equals(literal.kind())) {
      return Ty.named(BIGINT);
    }
    return Ty.named(token);
  }

  /** Returns the type with literal types widened to their primitive. */
  static Ty widen(final Ty type) {
    if (type instanceof Ty.Named named && named.args().isEmpty()) {
      final String name = named.name();
      if (name.startsWith("\"")) {
        return Ty.named(STRING);
      }
      if ("true".equals(name) || "false".equals(name)) {
        return Ty.named(BOOLEAN);
      }
      if (!name.isEmpty() && (Character.isDigit(name.charAt(0)) || name.charAt(0) == '-')) {
        return Ty.named(NUMBER);
      }
      return type;
    }
    if (type instanceof Ty.Union union) {
      return new Ty.Union(union.parts().stream().map(Literals::widen).toList());
    }
    return type;
  }
}
