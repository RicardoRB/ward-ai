package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.frontend.antlr.format.rust.RustRoles.Role;
import dev.ward.analysis.frontend.antlr.rust.RustLexer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * Classifies every token for spacing. Whether {@code -}, {@code *}, {@code &}, {@code !} and
 * {@code ?} are prefix or binary follows from the token before; the roles settle the rest.
 */
final class RustKinds {
  private static final String BANG = "!";
  private static final String QUESTION = "?";
  private static final Set<String> OPERAND_WORDS =
      Set.of(
          "self",
          "Self",
          "super",
          "crate",
          "$crate",
          "true",
          "false",
          "await",
          "_",
          "macro_rules",
          "union",
          "auto",
          "default",
          "raw",
          "safe"
      );
  private static final Set<String> PREFIX_CAPABLE = Set.of("-", "*", "&", "&&");
  private static final Set<String> RANGES = Set.of("..", "..=", "...");
  private static final Map<String, RustKind> PUNCTUATION =
      Map.ofEntries(
          Map.entry("(", RustKind.OPEN),
          Map.entry("[", RustKind.OPEN),
          Map.entry(")", RustKind.CLOSE),
          Map.entry("]", RustKind.CLOSE),
          Map.entry("{", RustKind.LEFT_BRACE),
          Map.entry("}", RustKind.RIGHT_BRACE),
          Map.entry(",", RustKind.COMMA),
          Map.entry(";", RustKind.SEMI),
          Map.entry(":", RustKind.COLON),
          Map.entry(".", RustKind.DOT),
          Map.entry("::", RustKind.PATH_SEPARATOR),
          Map.entry("#", RustKind.POUND),
          Map.entry("$", RustKind.DOLLAR)
      );
  private static final Set<String> OPERATORS =
      Set.of(
          "=",
          "==",
          "!=",
          "<",
          ">",
          "<=",
          ">=",
          "+=",
          "-=",
          "*=",
          "/=",
          "%=",
          "^=",
          "&=",
          "|=",
          "<<=",
          ">>=",
          "=>",
          "->",
          "+",
          "/",
          "%",
          "^",
          "|",
          "@",
          "&&",
          "||"
      );

  private RustKinds() {}

  /**
   * Classifies the tokens in order.
   *
   * @param tokens the significant tokens
   * @param roles their roles
   * @return one kind per token, in token order
   */
  static List<RustKind> of(final List<Token> tokens, final RustRoles roles) {
    final List<RustKind> kinds = new ArrayList<>();
    for (int i = 0; i < tokens.size(); i++) {
      final RustKind previous = i == 0 ? RustKind.COMMENT : kinds.get(i - 1);
      final Token before = i == 0 ? null : tokens.get(i - 1);
      kinds.add(classify(tokens.get(i), before, previous, roles.at(i)));
    }
    return List.copyOf(kinds);
  }

  /**
   * Tells whether a token of this kind can end an operand, so that an operator after it is binary.
   *
   * @param kind the kind
   * @return true for names, literals, closing brackets and generic closers
   */
  static boolean endsOperand(final RustKind kind) {
    return kind == RustKind.OPERAND
        || kind == RustKind.CLOSE
        || kind == RustKind.ANGLE_CLOSE
        || kind == RustKind.POSTFIX;
  }

  private static RustKind classify(
      final Token token,
      final Token before,
      final RustKind previous,
      final Role role
  ) {
    final RustKind byRole = byRole(role);
    if (byRole != null) {
      return byRole;
    }
    if (token.getChannel() != Token.DEFAULT_CHANNEL) {
      return RustKind.COMMENT;
    }
    final String text = token.getText();
    final RustKind punctuation = PUNCTUATION.get(text);
    if (punctuation != null) {
      return punctuation;
    }
    if (RANGES.contains(text)) {
      return RustKind.RANGE;
    }
    final RustKind operator = operator(token, before, previous);
    return operator == null ? word(token) : operator;
  }

  private static RustKind byRole(final Role role) {
    return switch (role) {
      case OPEN_ATTACHED -> RustKind.ANGLE_OPEN;
      case OPEN_QUALIFIED -> RustKind.QUALIFIED_OPEN;
      case CLOSE_ANGLE -> RustKind.ANGLE_CLOSE;
      case CLOSURE_OPEN -> RustKind.PIPE_OPEN;
      case CLOSURE_CLOSE -> RustKind.PIPE_CLOSE;
      case PATTERN_BAR -> RustKind.PATTERN_BAR;
      default -> null;
    };
  }

  private static RustKind operator(final Token token, final Token before, final RustKind previous) {
    final String text = token.getText();
    final boolean operand = endsOperand(previous);
    if (BANG.equals(text)) {
      return bang(token, before, previous);
    }
    if (QUESTION.equals(text)) {
      return operand ? RustKind.POSTFIX : RustKind.PREFIX;
    }
    if (PREFIX_CAPABLE.contains(text) && !operand) {
      return RustKind.PREFIX;
    }
    if ("||".equals(text) && !operand) {
      return RustKind.EMPTY_CLOSURE;
    }
    return OPERATORS.contains(text) || PREFIX_CAPABLE.contains(text) ? RustKind.BINARY : null;
  }

  private static RustKind bang(final Token token, final Token before, final RustKind previous) {
    if (before != null && "->".equals(before.getText())) {
      return RustKind.OPERAND;
    }
    return isBang(token, before, previous) ? RustKind.BANG : RustKind.PREFIX;
  }

  private static boolean isBang(final Token token, final Token before, final RustKind previous) {
    return previous == RustKind.OPERAND
        && before != null
        && before.getStopIndex() + 1 == token.getStartIndex();
  }

  private static RustKind word(final Token token) {
    final String name = RustLexer.VOCABULARY.getSymbolicName(token.getType());
    final String text = token.getText();
    if (OPERAND_WORDS.contains(text) || name == null) {
      return RustKind.OPERAND;
    }
    if (RustLexer.LIFETIME_OR_LABEL == token.getType()) {
      return RustKind.LIFETIME;
    }
    return name.startsWith("KW_") ? RustKind.KEYWORD : RustKind.OPERAND;
  }
}
