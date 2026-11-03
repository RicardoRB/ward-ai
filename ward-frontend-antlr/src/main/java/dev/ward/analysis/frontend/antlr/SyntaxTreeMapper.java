package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Maps an ANTLR parse tree to the shared {@link SyntaxNode} shape. Comments on the hidden channel
 * become {@code comment} nodes placed before the outermost node that starts at the token after
 * them, so a rule sees a documentation comment as the sibling of the declaration it documents.
 */
final class SyntaxTreeMapper {

  /** The kind of a token the parser could not place in the tree. */
  static final String ERROR_KIND = "error";

  /** The kind of a comment node. */
  static final String COMMENT_KIND = "comment";

  private final Parser parser;
  private final TokenStream tokens;

  /** The index of the next token whose hidden tokens have not been emitted. */
  private int nextToken;

  private SyntaxTreeMapper(final Parser parser) {
    this.parser = parser;
    this.tokens = parser.getTokenStream();
  }

  /**
   * Maps the tree under {@code node}. Rule nodes take their rule name as kind; terminal nodes take
   * the token's display name and carry its text.
   *
   * @param node the root of the ANTLR tree
   * @param parser the parser that produced the tree, for rule and token names
   * @return the mapped tree
   */
  static SyntaxNode map(final ParseTree node, final Parser parser) {
    return new SyntaxTreeMapper(parser).node(node);
  }

  private SyntaxNode node(final ParseTree node) {
    if (node instanceof ErrorNode error) {
      return terminal(ERROR_KIND, error.getSymbol());
    }
    if (node instanceof TerminalNode terminal) {
      return terminal(parser.getVocabulary(), terminal.getSymbol());
    }
    return rule((ParserRuleContext) node);
  }

  private SyntaxNode rule(final ParserRuleContext context) {
    final List<SyntaxNode> children = new ArrayList<>();
    for (int i = 0; i < context.getChildCount(); i++) {
      final ParseTree child = context.getChild(i);
      children.addAll(commentsBefore(firstToken(child)));
      children.add(node(child));
    }
    final String kind = parser.getRuleNames()[context.getRuleIndex()];
    return new SyntaxNode(kind, span(context), Map.of(), children);
  }

  private static Token firstToken(final ParseTree node) {
    if (node instanceof ParserRuleContext context) {
      return context.getStart();
    }
    return ((TerminalNode) node).getSymbol();
  }

  /** Emits the comments between the last emitted token and {@code first}, in source order. */
  private List<SyntaxNode> commentsBefore(final Token first) {
    final List<SyntaxNode> comments = new ArrayList<>();
    while (nextToken < first.getTokenIndex()) {
      final Token hidden = tokens.get(nextToken);
      if (hidden.getChannel() == Token.HIDDEN_CHANNEL && !hidden.getText().isBlank()) {
        comments.add(comment(hidden));
      }
      nextToken++;
    }
    return comments;
  }

  private static SyntaxNode comment(final Token token) {
    return new SyntaxNode(
        COMMENT_KIND, commentSpan(token), Map.of("text", token.getText()), List.of());
  }

  private static Span commentSpan(final Token token) {
    final String text = token.getText();
    final int newlines = (int) text.chars().filter(c -> c == '\n').count();
    final int endLine = token.getLine() + newlines;
    final int endColumn =
        newlines == 0
            ? token.getCharPositionInLine() + text.length()
            : text.length() - text.lastIndexOf('\n') - 1;
    return new Span(
        token.getLine(),
        token.getCharPositionInLine() + 1,
        endLine,
        endColumn,
        Math.max(0, token.getStartIndex()),
        Math.max(0, token.getStopIndex() + 1));
  }

  private static SyntaxNode terminal(final Vocabulary vocabulary, final Token token) {
    return terminal(displayName(vocabulary, token.getType()), token);
  }

  private static SyntaxNode terminal(final String kind, final Token token) {
    final Span span = tokenSpan(token);
    return new SyntaxNode(kind, span, Map.of("text", token.getText()), List.of());
  }

  private static String displayName(final Vocabulary vocabulary, final int type) {
    final String name = vocabulary.getDisplayName(type);
    return name == null || name.isEmpty() ? "token" : name;
  }

  private static Span span(final ParserRuleContext context) {
    final Token start = context.getStart();
    final Token stop = context.getStop();
    if (stop == null || stop.getStopIndex() < start.getStartIndex()) {
      return zeroWidthSpan(start);
    }
    final int startOffset = Math.max(0, start.getStartIndex());
    final int endOffset = Math.max(startOffset, stop.getStopIndex() + 1);
    final int endLine = stop.getLine();
    final int endColumn = stop.getCharPositionInLine() + tokenWidth(stop);
    if (endLine < start.getLine()
        || endLine == start.getLine() && endColumn < start.getCharPositionInLine() + 1) {
      return zeroWidthSpan(start);
    }
    return new Span(
        start.getLine(),
        start.getCharPositionInLine() + 1,
        endLine,
        endColumn,
        startOffset,
        endOffset);
  }

  private static Span tokenSpan(final Token token) {
    final int startOffset = Math.max(0, token.getStartIndex());
    final int endOffset = Math.max(startOffset, token.getStopIndex() + 1);
    return new Span(
        token.getLine(),
        token.getCharPositionInLine() + 1,
        token.getLine(),
        token.getCharPositionInLine() + tokenWidth(token),
        startOffset,
        endOffset);
  }

  private static Span zeroWidthSpan(final Token start) {
    final int startOffset = Math.max(0, start.getStartIndex());
    final int column = start.getCharPositionInLine() + 1;
    return new Span(start.getLine(), column, start.getLine(), column, startOffset, startOffset);
  }

  private static int tokenWidth(final Token token) {
    return Math.max(1, token.getStopIndex() - token.getStartIndex() + 1);
  }
}
