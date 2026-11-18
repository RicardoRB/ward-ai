package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.frontend.antlr.java.JavaLexer;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

/** The member rules of Java: a block after a type keyword is a type body, any other one a body. */
final class JavaMembers implements MemberRules {
  private boolean type;
  private boolean enumeration;
  private boolean assigned;

  @Override
  public List<Token> lex(final String path, final String text) {
    final JavaLexer lexer = new JavaLexer(CharStreams.fromString(text));
    lexer.removeErrorListeners();
    lexer.startFindings(path);
    final List<Token> tokens =
        lexer.getAllTokens().stream().filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
            .map(Token.class::cast).toList();
    return lexer.findings().isEmpty() ? tokens : List.of();
  }

  @Override
  public Kind kind(final Token token) {
    return switch (token.getType()) {
      case JavaLexer.LBRACE -> Kind.OPEN;
      case JavaLexer.RBRACE -> Kind.CLOSE;
      case JavaLexer.LPAREN -> Kind.LEFT_PAREN;
      case JavaLexer.RPAREN -> Kind.RIGHT_PAREN;
      case JavaLexer.SEMI -> Kind.END;
      default -> Kind.OTHER;
    };
  }

  @Override
  public void see(final Token token, final Token previous, final boolean firstOnLine) {
    final int kind = token.getType();
    final int before = previous == null ? -1 : previous.getType();
    if (kind == JavaLexer.ASSIGN) {
      assigned = true;
    } else if (kind == JavaLexer.ENUM) {
      type = true;
      enumeration = true;
    } else if (kind == JavaLexer.INTERFACE
        || kind == JavaLexer.CLASS && before != JavaLexer.DOT
        || kind == JavaLexer.IDENTIFIER && before == JavaLexer.RECORD) {
      type = true;
    }
  }

  @Override
  public Action open(final Token previous, final boolean insideType, final boolean enumConstants) {
    final boolean array =
        previous != null
            && (previous.getType() == JavaLexer.DEFAULT || previous.getType() == JavaLexer.ASSIGN);
    if (assigned || array || enumConstants && !type) {
      return Action.KEEP;
    }
    if (type) {
      return Action.TYPE;
    }
    return insideType ? Action.STRIP : Action.KEEP;
  }

  @Override
  public boolean enumHead() {
    return enumeration;
  }

  @Override
  public void reset() {
    type = false;
    enumeration = false;
    assigned = false;
  }
}
