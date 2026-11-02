package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/** Maps an ANTLR parse tree to the shared {@link SyntaxNode} shape. */
final class SyntaxTreeMapper {

  /** The kind of a token the parser could not place in the tree. */
  static final String ERROR_KIND = "error";

  private SyntaxTreeMapper() {}

  /**
   * Maps the tree under {@code node}. Rule nodes take their rule name as kind; terminal nodes take
   * the token's display name and carry its text.
   *
   * @param node the root of the ANTLR tree
   * @param parser the parser that produced the tree, for rule and token names
   * @return the mapped tree
   */
  static SyntaxNode map(final ParseTree node, final Parser parser) {
    if (node instanceof ErrorNode error) {
      return terminal(ERROR_KIND, error.getSymbol());
    }
    if (node instanceof TerminalNode terminal) {
      return terminal(parser.getVocabulary(), terminal.getSymbol());
    }
    return rule((ParserRuleContext) node, parser);
  }

  private static SyntaxNode rule(final ParserRuleContext context, final Parser parser) {
    final List<SyntaxNode> children = new ArrayList<>();
    for (int i = 0; i < context.getChildCount(); i++) {
      children.add(map(context.getChild(i), parser));
    }
    final String kind = parser.getRuleNames()[context.getRuleIndex()];
    return new SyntaxNode(kind, span(context), Map.of(), children);
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
