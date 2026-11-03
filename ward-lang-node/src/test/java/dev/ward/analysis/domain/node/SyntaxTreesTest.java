package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SyntaxTreesTest {

  private static final Span LINE = new Span(1, 1, 1, 2, 0, 1);

  @Test
  void withoutErrorsDropsErrorNodesAtAnyDepthAndKeepsTheRest() {
    final SyntaxNode tree =
        node(
            "functionBody",
            node("ifStatement", node("error")),
            node("error"),
            node("singleExpression"));

    final SyntaxNode pruned = SyntaxTrees.withoutErrors(tree);

    assertThat(pruned.children())
        .extracting(SyntaxNode::kind)
        .containsExactly("ifStatement", "singleExpression");
    assertThat(pruned.children().get(0).children()).isEmpty();
  }

  private static SyntaxNode node(final String kind, final SyntaxNode... children) {
    return new SyntaxNode(kind, LINE, Map.of(), List.of(children));
  }
}
