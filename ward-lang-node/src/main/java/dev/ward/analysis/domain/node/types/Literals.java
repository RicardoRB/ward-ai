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
  static Optional<TsType> of(final SyntaxNode literal) {
    final List<String> tokens = TreeText.leaves(literal);
    if (tokens.size() != 1) {
      return Optional.of(TsType.named(STRING)).filter(ignored -> isTemplate(tokens));
    }
    final String token = tokens.get(0);
    return Optional.of(typeOfToken(token, literal));
  }

  private static boolean isTemplate(final List<String> tokens) {
    return !tokens.isEmpty() && tokens.get(0).startsWith("`");
  }

  private static TsType typeOfToken(final String token, final SyntaxNode literal) {
    if (token.startsWith("\"") || token.startsWith("'")) {
      return TsType.named("\"" + TreeText.unquote(token) + "\"");
    }
    return switch (token) {
      case "true", "false", "null" -> TsType.named(token);
      default -> numeric(token, literal);
    };
  }

  private static TsType numeric(final String token, final SyntaxNode literal) {
    if (token.startsWith("/")) {
      return TsType.named("RegExp");
    }
    if (token.endsWith("n") && !token.startsWith("0x") || "bigintLiteral".equals(literal.kind())) {
      return TsType.named(BIGINT);
    }
    return TsType.named(token);
  }

  /** Returns the type with literal types widened to their primitive. */
  static TsType widen(final TsType type) {
    if (type instanceof TsType.Named named && named.args().isEmpty()) {
      final String name = named.name();
      if (name.startsWith("\"")) {
        return TsType.named(STRING);
      }
      if ("true".equals(name) || "false".equals(name)) {
        return TsType.named(BOOLEAN);
      }
      if (!name.isEmpty() && (Character.isDigit(name.charAt(0)) || name.charAt(0) == '-')) {
        return TsType.named(NUMBER);
      }
      return type;
    }
    if (type instanceof TsType.Union union) {
      return new TsType.Union(union.parts().stream().map(Literals::widen).toList());
    }
    return type;
  }
}
