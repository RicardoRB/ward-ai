package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SyntaxWalkDescendantsTest {

  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  @Test
  void descendantsAreReturnedInSourceOrderAtEveryDepth() {
    final SyntaxNode root = node("root", node("a", node("a1"), node("a2", node("a2x"))), node("b"));

    final List<String> kinds = SyntaxWalk.descendants(root).stream().map(SyntaxNode::kind).toList();

    assertThat(kinds).containsExactly("a", "a1", "a2", "a2x", "b");
  }

  @Test
  void descendantsOfALeafAreEmpty() {
    assertThat(SyntaxWalk.descendants(node("leaf"))).isEmpty();
  }

  private static SyntaxNode node(final String kind, final SyntaxNode... children) {
    return new SyntaxNode(kind, SPAN, Map.of(), List.of(children));
  }
}
