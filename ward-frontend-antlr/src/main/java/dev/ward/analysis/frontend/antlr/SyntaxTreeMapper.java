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

  /** What separates a rule name from the name of its variant. */
  private static final String VARIANT = "__";

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
    return rule((ParserRuleContext)node);
  }

  private SyntaxNode rule(final ParserRuleContext context) {
    final List<SyntaxNode> children = new ArrayList<>();
    for (int i = 0; i < context.getChildCount(); i++) {
      final ParseTree child = context.getChild(i);
      children.addAll(commentsBefore(firstToken(child)));
      children.add(node(child));
    }
    return new SyntaxNode(kind(context), SyntaxSpans.span(context), Map.of(), children);
  }

  /**
   * Returns the kind of a rule node: its rule name, without the variant a grammar adds after
   * {@value #VARIANT}. A rule {@code expression__condition} reads a part of what {@code expression}
   * reads, and the rules that read the tree see one kind for both.
   */
  private String kind(final ParserRuleContext context) {
    final String name = parser.getRuleNames() [context.getRuleIndex()];
    final int variant = name.lastIndexOf(VARIANT);
    return variant < 0 ? name : name.substring(0, variant);
  }

  private static Token firstToken(final ParseTree node) {
    if (node instanceof ParserRuleContext context) {
      return context.getStart();
    }
    return ((TerminalNode)node).getSymbol();
  }

  /** Emits the comments between the last emitted token and {@code first}, in source order. */
  private List<SyntaxNode> commentsBefore(final Token first) {
    final List<SyntaxNode> comments = new ArrayList<>();
    while (nextToken < first.getTokenIndex()) {
      final Token hidden = tokens.get(nextToken);
      if (hidden.getChannel() == Token.HIDDEN_CHANNEL && !hidden.getText().isBlank()) {
        comments.add(comment(hidden));
      } else if (SkippedSections.is(tokens, hidden)) {
        comments.add(SkippedSections.node(hidden));
      }
      nextToken++;
    }
    return comments;
  }

  private static SyntaxNode comment(final Token token) {
    return new SyntaxNode(
        COMMENT_KIND,
        SyntaxSpans.comment(token),
        Map.of("text", token.getText()),
        List.of()
    );
  }

  private static SyntaxNode terminal(final Vocabulary vocabulary, final Token token) {
    return terminal(displayName(vocabulary, token.getType()), token);
  }

  private static SyntaxNode terminal(final String kind, final Token token) {
    final Span span = SyntaxSpans.token(token);
    return new SyntaxNode(kind, span, Map.of("text", token.getText()), List.of());
  }

  private static String displayName(final Vocabulary vocabulary, final int type) {
    final String name = vocabulary.getDisplayName(type);
    return name == null || name.isEmpty() ? "token" : name;
  }
}
