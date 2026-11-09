package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.domain.format.FormatToken;
import dev.ward.analysis.frontend.antlr.format.ecma.EcmaToken.Kind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalInt;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;

/**
 * Turns the ANTLR token stream into layout tokens. A template literal, from its opening backtick to
 * its closing one, becomes one token whose text is its exact source, so no space inside it moves.
 */
final class EcmaTokenCollector {

  private static final String COMMENT_TYPE = "comment";
  private static final String LINE_COMMENT = "SingleLineComment";
  private static final String WHITESPACES = "WhiteSpaces";
  private static final String LINE_TERMINATOR = "LineTerminator";
  private static final String BACKTICK = "`";
  private static final String OPEN_BRACE = "{";
  private static final String CLOSE_BRACE = "}";
  private static final String TEMPLATE_EXPRESSION = "${";
  private static final String UNEXPECTED = "UnexpectedCharacter";

  /** What an open bracket of a template literal stands for. */
  private enum Mode {
    TEMPLATE,
    EXPRESSION,
    BRACE
  }

  private final Vocabulary vocabulary;
  private final List<EcmaToken> tokens = new ArrayList<>();
  private final List<FormatToken> syntaxTokens = new ArrayList<>();
  private final Deque<Mode> modes = new ArrayDeque<>();
  private final List<String> template = new ArrayList<>();
  private int templateDepth;
  private int templateLine;
  private boolean templateSpace;
  private boolean pendingSpace;
  private int errorLine = Integer.MAX_VALUE;
  private final EcmaJsxText jsx = new EcmaJsxText();
  private int jsxLine;
  private boolean jsxSpace;

  /**
   * Binds the collector to the vocabulary of the lexer.
   *
   * @param vocabulary the token names of the lexer
   */
  EcmaTokenCollector(final Vocabulary vocabulary) {
    this.vocabulary = vocabulary;
  }

  /**
   * Adds one token of the stream, in order.
   *
   * @param token the next token
   */
  void accept(final Token token) {
    if (token.getType() == Token.EOF) {
      return;
    }
    final String name = vocabulary.getSymbolicName(token.getType());
    final boolean hidden = token.getChannel() != Token.DEFAULT_CHANNEL;
    final boolean whitespace = hidden && isWhitespace(name);
    if (UNEXPECTED.equals(name)) {
      errorLine = Math.min(errorLine, token.getLine());
    }
    if (!whitespace) {
      final String type = hidden ? COMMENT_TYPE : vocabulary.getDisplayName(token.getType());
      syntaxTokens.add(new FormatToken(type, token.getText(), token.getLine()));
    }
    dispatch(token, name, hidden, whitespace);
  }

  /** Routes one token to JSX, layout whitespace, a template or a plain layout token. */
  private void dispatch(
      final Token token, final String name, final boolean hidden, final boolean whitespace) {
    final String text = token.getText();
    if (jsx.active() || (templateDepth == 0 && EcmaJsxText.OPEN.equals(name))) {
      acceptJsx(name, token);
    } else if (whitespace) {
      acceptWhitespace(text);
    } else if (templateDepth > 0 || (!hidden && BACKTICK.equals(text))) {
      acceptTemplateToken(token, text);
    } else {
      tokens.add(new EcmaToken(text, token.getLine(), kind(name, hidden), pendingSpace));
      pendingSpace = false;
    }
  }

  /**
   * Adds the text of a JSX element, from its {@code <} to the matching end, as one token. The
   * formatter keeps JSX as the author wrote it, so no token inside the element moves.
   */
  private void acceptJsx(final String name, final Token token) {
    if (!jsx.active()) {
      jsxLine = token.getLine();
      jsxSpace = pendingSpace;
      pendingSpace = false;
    }
    if (jsx.add(name, token.getText())) {
      tokens.add(new EcmaToken(jsx.take(), jsxLine, Kind.CODE, jsxSpace));
    }
  }

  /**
   * Builds the parse once every token has been added.
   *
   * @param rules the parse-tree rule kinds in pre-order
   * @param errorLine the first syntax error line, if any
   * @return the parse
   */
  EcmaParse build(final List<FormatRule> rules, final OptionalInt parserErrorLine) {
    final OptionalInt first =
        errorLine == Integer.MAX_VALUE
            ? parserErrorLine
            : parserErrorLine.isPresent()
                ? OptionalInt.of(Math.min(errorLine, parserErrorLine.getAsInt()))
                : OptionalInt.of(errorLine);
    return new EcmaParse(tokens, syntaxTokens, rules, first);
  }

  private void acceptWhitespace(final String text) {
    if (templateDepth > 0) {
      template.add(text);
    } else {
      pendingSpace = true;
    }
  }

  private void acceptTemplateToken(final Token token, final String text) {
    if (templateDepth == 0) {
      templateLine = token.getLine();
      templateSpace = pendingSpace;
      pendingSpace = false;
      template.clear();
    }
    template.add(text);
    updateModes(text);
    if (templateDepth == 0) {
      tokens.add(new EcmaToken(String.join("", template), templateLine, Kind.CODE, templateSpace));
    }
  }

  private void updateModes(final String text) {
    if (BACKTICK.equals(text)) {
      toggleTemplate();
    } else if (TEMPLATE_EXPRESSION.equals(text)) {
      modes.push(Mode.EXPRESSION);
    } else if (OPEN_BRACE.equals(text)) {
      modes.push(Mode.BRACE);
    } else if (CLOSE_BRACE.equals(text) && !modes.isEmpty()) {
      modes.pop();
    }
  }

  private void toggleTemplate() {
    if (!modes.isEmpty() && modes.peek() == Mode.TEMPLATE) {
      modes.pop();
      templateDepth--;
    } else {
      modes.push(Mode.TEMPLATE);
      templateDepth++;
    }
  }

  private static Kind kind(final String name, final boolean hidden) {
    if (!hidden) {
      return Kind.CODE;
    }
    return LINE_COMMENT.equals(name) ? Kind.LINE_COMMENT : Kind.BLOCK_COMMENT;
  }

  private static boolean isWhitespace(final String name) {
    return WHITESPACES.equals(name) || LINE_TERMINATOR.equals(name);
  }
}
