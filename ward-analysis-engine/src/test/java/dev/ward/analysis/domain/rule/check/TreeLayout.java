package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;

/** Lays out hand-made syntax trees on one line, each token after the previous one. */
final class TreeLayout {

  private int offset;

  /** A token with text. */
  SyntaxNode leaf(final String kind, final String text) {
    final int start = offset;
    offset += text.length() + 1;
    return new SyntaxNode(
        kind,
        new Span(1, start + 1, 1, start + text.length(), start, start + text.length()),
        Map.of("text", text),
        List.of());
  }

  /** A node spanning its children, which must be given in source order. */
  SyntaxNode node(final String kind, final SyntaxNode... children) {
    final Span first = children[0].span();
    final Span last = children[children.length - 1].span();
    return new SyntaxNode(
        kind,
        new Span(
            first.startLine(),
            first.startColumn(),
            last.endLine(),
            last.endColumn(),
            first.startOffset(),
            last.endOffset()),
        Map.of(),
        List.of(children));
  }

  /** The start offsets of the matches, to tell which token each one is at. */
  static List<Integer> starts(final List<RuleMatch> matches) {
    return matches.stream().map(match -> match.span().startOffset()).toList();
  }

  static List<String> messages(final List<RuleMatch> matches) {
    return matches.stream().map(RuleMatch::message).toList();
  }
}
