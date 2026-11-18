package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.frontend.antlr.kotlin.KotlinLexer;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

/**
 * The member rules of Kotlin. Only the block of a function, a constructor, an {@code init} or an
 * accessor that has no {@code =} is a body to blank: a block after {@code =} or {@code by} may give
 * the type of a declaration, so it stays.
 */
final class KotlinMembers implements MemberRules {
  private boolean type;
  private boolean assigned;
  private boolean delegated;
  private boolean function;

  @Override
  public List<Token> lex(final String path, final String text) {
    final KotlinLexer lexer = new KotlinLexer(CharStreams.fromString(text));
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
      case KotlinLexer.LCURL -> Kind.OPEN;
      case KotlinLexer.LineStrExprStart, KotlinLexer.MultiLineStrExprStart -> Kind.TEMPLATE_OPEN;
      case KotlinLexer.RCURL -> Kind.CLOSE;
      case KotlinLexer.LPAREN -> Kind.LEFT_PAREN;
      case KotlinLexer.RPAREN -> Kind.RIGHT_PAREN;
      case KotlinLexer.SEMICOLON -> Kind.END;
      case KotlinLexer.NL -> Kind.NEWLINE;
      default -> Kind.OTHER;
    };
  }

  @Override
  public void see(final Token token, final Token previous, final boolean firstOnLine) {
    final int kind = token.getType();
    final int before = previous == null ? -1 : previous.getType();
    if (before == KotlinLexer.DOT || before == KotlinLexer.COLONCOLON) {
      return;
    }
    switch (kind) {
      case KotlinLexer.ASSIGNMENT -> assigned = true;
      case KotlinLexer.BY -> delegated = true;
      case KotlinLexer.CLASS, KotlinLexer.INTERFACE -> startType(true);
      case KotlinLexer.OBJECT -> startType(!assigned);
      case KotlinLexer.FUN, KotlinLexer.INIT, KotlinLexer.CONSTRUCTOR -> startFunction();
      case KotlinLexer.VAL, KotlinLexer.VAR -> reset();
      default -> accessor(token, firstOnLine);
    }
  }

  private void startFunction() {
    reset();
    function = true;
  }

  private void accessor(final Token token, final boolean firstOnLine) {
    if (firstOnLine && isAccessor(token)) {
      startFunction();
    }
  }

  private static boolean isAccessor(final Token token) {
    return token.getType() == KotlinLexer.GETTER || token.getType() == KotlinLexer.SETTER;
  }

  private void startType(final boolean start) {
    if (start) {
      reset();
      type = true;
    }
  }

  @Override
  public Action open(final Token previous, final boolean insideType, final boolean enumConstants) {
    if (assigned) {
      return Action.KEEP;
    }
    if (type) {
      return Action.TYPE;
    }
    return function && !delegated ? Action.STRIP : Action.KEEP;
  }

  @Override
  public boolean enumHead() {
    return false;
  }

  @Override
  public void reset() {
    type = false;
    assigned = false;
    delegated = false;
    function = false;
  }
}
