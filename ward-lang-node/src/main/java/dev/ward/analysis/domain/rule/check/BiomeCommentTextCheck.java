package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a comment written as JSX text, a line comment or a block comment, at the comment's start,
 * as Biome 2.5.15's {@code noCommentText} does. A line comment inside a URL ({@code ://}) and an
 * unterminated block comment are not reported.
 */
public final class BiomeCommentTextCheck implements RuleCheck {

  private static final String TEXT = "jsxText";
  private static final char SLASH = '/';
  private static final char STAR = '*';
  private static final char COLON = ':';
  private static final char NEWLINE = '\n';

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeCommentTextCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Always fails: the comment positions are read from the source text, which only the unit gives.
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the comment-text check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    final String source = unit.text();
    return SyntaxWalk.visits(root, Set.of(TEXT)).stream()
        .flatMap(visit -> commentStarts(visit.node(), source).stream())
        .map(offset -> at(source, offset))
        .toList();
  }

  /** The offsets in the source where a comment starts within one JSX text node. */
  private static List<Integer> commentStarts(final SyntaxNode text, final String source) {
    final int base = text.span().startOffset();
    final String raw = source.substring(base, text.span().endOffset());
    final List<Integer> starts = new java.util.ArrayList<>();
    int index = 0;
    while (index + 1 < raw.length()) {
      if (raw.charAt(index) != SLASH) {
        index++;
        continue;
      }
      final char next = raw.charAt(index + 1);
      if (next == SLASH && (index == 0 || raw.charAt(index - 1) != COLON)) {
        starts.add(base + index);
        final int newline = raw.indexOf(NEWLINE, index);
        index = newline < 0 ? raw.length() : newline + 1;
      } else if (next == STAR) {
        final int end = raw.indexOf("*/", index + 2);
        if (end >= 0) {
          starts.add(base + index);
        }
        index = end < 0 ? raw.length() : end + 2;
      } else {
        index += 2;
      }
    }
    return starts;
  }

  private RuleMatch at(final String source, final int offset) {
    int line = 1;
    int lineStart = 0;
    for (int i = 0; i < offset; i++) {
      if (source.charAt(i) == NEWLINE) {
        line++;
        lineStart = i + 1;
      }
    }
    final int column = offset - lineStart + 1;
    return new RuleMatch(new Span(line, column, line, column + 2, offset, offset + 2), message);
  }
}
