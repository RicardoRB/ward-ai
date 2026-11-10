package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;

/** Builders and runners for the check tests that need tokens on given lines. */
final class TokenTrees {

  private TokenTrees() {}

  /** A token of {@code kind} with {@code text} on {@code line}, starting at {@code offset}. */
  static SyntaxNode tok(final String kind, final String text, final int line, final int offset) {
    return new SyntaxNode(
        kind,
        new Span(line, offset + 1, line, offset + text.length(), offset, offset + text.length()),
        Map.of("text", text),
        List.of());
  }

  /** A node of {@code kind} that spans from its first child to its last. */
  static SyntaxNode group(final String kind, final SyntaxNode... children) {
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

  /** The matches of a tree check over {@code root} wrapped in a unit. */
  static List<RuleMatch> unitMatches(final RuleCheck check, final SyntaxNode root) {
    return check.matches(TreeBuilders.unit(root));
  }

  /** The matches of a unit check over {@code root} in a file with {@code text}. */
  static List<RuleMatch> fileMatches(
      final UnitCheck check, final String text, final SyntaxNode root) {
    return check.check(TreeBuilders.unit(root), new SourceFile("p.java", text));
  }
}
