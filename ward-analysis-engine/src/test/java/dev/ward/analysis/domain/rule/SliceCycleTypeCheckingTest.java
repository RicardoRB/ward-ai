package dev.ward.analysis.domain.rule;

import static dev.ward.analysis.domain.rule.GraphRuleFixtures.definition;
import static dev.ward.analysis.domain.rule.GraphRuleFixtures.edge;
import static dev.ward.analysis.domain.rule.GraphRuleFixtures.graph;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.Reference;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The architecture cycle rule keeps counting imports under a type-checking guard as edges. */
class SliceCycleTypeCheckingTest {

  private static final String ROOT = "src/main/java/com/acme";

  @Test
  void countsAnEdgeMarkedAsTypeCheckingLikeAnyOther() {
    final DependencyEdge plain =
        edge(ROOT + "/orders/Order.java", ROOT + "/billing/Invoice.java", 3);
    final DependencyEdge guarded =
        edge(ROOT + "/billing/Invoice.java", ROOT + "/orders/Order.java", 5);
    final DependencyEdge typeOnly =
        new DependencyEdge(
            guarded.from(),
            guarded.to(),
            new Reference(guarded.reference().specifier(), guarded.reference().span(), true));
    final DependencyGraph graph = graph(plain, typeOnly);
    final SliceCycleRule rule =
        new SliceCycleRule(
            definition(SliceCycleRule.ID, GraphRuleBody.encodeRoots(List.of(ROOT))), List.of(ROOT));

    assertThat(rule.matches(graph)).hasSize(1);
  }
}
