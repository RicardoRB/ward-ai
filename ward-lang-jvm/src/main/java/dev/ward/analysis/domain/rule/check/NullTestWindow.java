package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The source proxy for SpotBugs' "many preceding null tests": SpotBugs counts the null-test
 * instructions in the 30 bytes of bytecode before the place that makes a value null and, with at
 * least three, reports a dereference of it as "might be infeasible". The proxy counts the null
 * tests ({@code == null}, {@code != null}, {@code Objects.isNull}, {@code Objects.nonNull}) of any
 * variable in the {@value #WINDOW} statements of the same block that come before the statement
 * holding the source, and asks for {@value #MANY} of them.
 */
final class NullTestWindow {

  private static final int WINDOW = 4;
  private static final int MANY = 3;
  private static final String BLOCK = "block";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final Pattern NULL_TEST =
      Pattern.compile("==null|!=null|null==|null!=|Objects\\.(?:isNull|nonNull)\\(");

  private NullTestWindow() {}

  /**
   * Turns a "null on some path" finding into the "might be infeasible" one when the null source
   * sits after many null tests; any other finding, or one with an unknown source, is returned as
   * is.
   */
  static NullAnalysis.Finding refine(final SyntaxNode root, final NullAnalysis.Finding finding) {
    final boolean candidate =
        finding.pattern() == NullAnalysis.Pattern.NULL_ON_SOME_PATH
            || finding.pattern() == NullAnalysis.Pattern.GUARANTEED_DEREF;
    if (candidate
        && !finding.sources().isEmpty()
        && finding.sources().stream().allMatch(source -> many(root, source))) {
      return new NullAnalysis.Finding(
          NullAnalysis.Pattern.NULL_ON_SOME_PATH_MIGHT_BE_INFEASIBLE,
          finding.at(),
          finding.sources());
    }
    return finding;
  }

  /** Returns the earlier of two spans. */
  static Span earlier(final Span left, final Span right) {
    return left.startOffset() <= right.startOffset() ? left : right;
  }

  /** Returns the expression of the last event, or null when there is none. */
  static SyntaxNode last(final List<NullEvent> events) {
    return events.isEmpty() ? null : events.get(events.size() - 1).node();
  }

  private static boolean many(final SyntaxNode root, final Span source) {
    SyntaxNode innermost = null;
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (BLOCK.equals(node.kind())
          && contains(node.span(), source)
          && (innermost == null || contains(innermost.span(), node.span()))) {
        innermost = node;
      }
    }
    return innermost != null && count(innermost, source) >= MANY;
  }

  private static int count(final SyntaxNode block, final Span source) {
    final List<SyntaxNode> statements =
        block.children().stream().filter(c -> BLOCK_STATEMENT.equals(c.kind())).toList();
    int holder = statements.size();
    for (int index = 0; index < statements.size(); index++) {
      if (contains(statements.get(index).span(), source)) {
        holder = index;
        break;
      }
    }
    int tests = 0;
    for (int index = Math.max(0, holder - WINDOW); index < holder; index++) {
      tests += (int) NULL_TEST.matcher(SyntaxWalk.text(statements.get(index))).results().count();
    }
    return tests;
  }

  private static boolean contains(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }
}
