package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.Set;

/**
 * Finds the code that a {@code try} guards with a {@code catch (NullPointerException)}, where a
 * null dereference is expected and handled.
 */
final class NullCatches {

  private NullCatches() {}

  /** Returns the spans of the try blocks of {@code root} that catch NullPointerException. */
  static Set<Span> of(final SyntaxNode root) {
    final Set<Span> out = new HashSet<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      final boolean attempt =
          !node.children().isEmpty() && "try".equals(SyntaxWalk.text(node.children().get(0)));
      if (attempt && catchesNull(node)) {
        node.children().stream()
            .filter(child -> "block".equals(child.kind()))
            .findFirst()
            .ifPresent(block -> out.add(block.span()));
      }
    }
    return out;
  }

  private static boolean catchesNull(final SyntaxNode attempt) {
    return SyntaxWalk.descendants(attempt).stream()
        .filter(node -> "catchClause".equals(node.kind()))
        .anyMatch(node -> SyntaxWalk.text(node).contains("NullPointerException"));
  }
}
