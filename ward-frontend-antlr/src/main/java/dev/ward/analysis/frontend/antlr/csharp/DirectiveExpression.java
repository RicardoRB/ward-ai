package dev.ward.analysis.frontend.antlr.csharp;

import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * Evaluates the condition of an {@code #if} or {@code #elif} directive: {@code !}, {@code &&},
 * {@code ||}, {@code ==}, {@code !=}, parentheses, {@code true}, {@code false} and symbols.
 */
final class DirectiveExpression {
  private final List<Token> tokens;
  private final Set<String> symbols;
  private int position;

  DirectiveExpression(final List<Token> tokens, final Set<String> symbols) {
    this.tokens = List.copyOf(tokens);
    this.symbols = Set.copyOf(symbols);
  }

  /** Returns the symbol that a {@code #define} or {@code #undef} line names, or null. */
  static String symbolOf(final List<Token> line) {
    return line.stream()
        .filter(token -> token.getType() == CSharpLexer.CONDITIONAL_SYMBOL)
        .map(Token::getText)
        .findFirst()
        .orElse(null);
  }

  boolean evaluate() {
    position = 0;
    return or();
  }

  private int peek() {
    if (position < tokens.size()) {
      final int type = tokens.get(position).getType();
      if (type != CSharpLexer.DIRECTIVE_NEW_LINE && type != Token.EOF) {
        return type;
      }
    }
    return -1;
  }

  private Token take() {
    final Token token = tokens.get(position);
    position++;
    return token;
  }

  private boolean or() {
    boolean value = and();
    while (peek() == CSharpLexer.TK_OR_OR) {
      take();
      value = and() || value;
    }
    return value;
  }

  private boolean and() {
    boolean value = equality();
    while (peek() == CSharpLexer.TK_AND_AND) {
      take();
      value = equality() && value;
    }
    return value;
  }

  private boolean equality() {
    final boolean value = unary();
    if (peek() == CSharpLexer.TK_EQ_EQ) {
      take();
      return value == unary();
    }
    if (peek() == CSharpLexer.TK_NOT_EQ) {
      take();
      return value != unary();
    }
    return value;
  }

  private boolean unary() {
    if (peek() == CSharpLexer.TK_NOT) {
      take();
      return !unary();
    }
    return primary();
  }

  private boolean primary() {
    final int type = peek();
    if (type == CSharpLexer.TRUE) {
      take();
      return true;
    }
    if (type == CSharpLexer.CONDITIONAL_SYMBOL) {
      return symbols.contains(take().getText());
    }
    if (type == CSharpLexer.TK_LPAREN) {
      take();
      final boolean value = or();
      if (peek() == CSharpLexer.TK_RPAREN) {
        take();
      }
      return value;
    }
    if (type == CSharpLexer.FALSE) {
      take();
    }
    return false;
  }
}
