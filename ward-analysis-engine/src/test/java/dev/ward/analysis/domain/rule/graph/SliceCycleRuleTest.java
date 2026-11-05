package dev.ward.analysis.domain.rule.graph;

import static dev.ward.analysis.domain.rule.graph.GraphRuleFixtures.definition;
import static dev.ward.analysis.domain.rule.graph.GraphRuleFixtures.edge;
import static dev.ward.analysis.domain.rule.graph.GraphRuleFixtures.graph;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SliceCycleRuleTest {

  private static final String ROOT = "src/main/java/com/acme";

  private static SliceCycleRule rule() {
    return new SliceCycleRule(
        definition(SliceCycleRule.ID, GraphRuleBody.encodeRoots(List.of(ROOT))), List.of(ROOT));
  }

  @Test
  void reportsTwoSlicesThatDependOnEachOtherOnceWithAPathOfTwoSteps() {
    final DependencyGraph graph =
        graph(
            edge(ROOT + "/orders/Order.java", ROOT + "/billing/Invoice.java", 3),
            edge(ROOT + "/billing/Invoice.java", ROOT + "/orders/Order.java", 5));

    final List<GraphMatch> matches = rule().matches(graph);

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().file()).isEqualTo(ROOT + "/billing/Invoice.java");
    assertThat(matches.getFirst().match().message())
        .isEqualTo("slices form a cycle: billing -> orders -> billing");
    assertThat(matches.getFirst().match().path()).hasSize(2);
    assertThat(matches.getFirst().match().path().getFirst().message())
        .isEqualTo("billing depends on orders");
  }

  @Test
  void reportsNothingForACycleInsideOneSlice() {
    final DependencyGraph graph =
        graph(
            edge(ROOT + "/orders/A.java", ROOT + "/orders/sub/B.java", 1),
            edge(ROOT + "/orders/sub/B.java", ROOT + "/orders/A.java", 1));

    assertThat(rule().matches(graph)).isEmpty();
  }

  @Test
  void reportsNothingForAFileDirectlyInTheRoot() {
    final DependencyGraph graph =
        graph(
            edge("src/index.ts", "src/orders/a.ts", 1), edge("src/orders/a.ts", "src/index.ts", 1));
    final SliceCycleRule rule =
        new SliceCycleRule(definition(SliceCycleRule.ID, "src"), List.of("src"));

    assertThat(rule.matches(graph)).isEmpty();
  }

  @Test
  void reportsOneCycleThroughTheAlphabeticallyFirstSliceAndItsShortestRing() {
    final DependencyGraph graph =
        graph(
            edge(ROOT + "/alpha/A.java", ROOT + "/beta/B.java", 1),
            edge(ROOT + "/beta/B.java", ROOT + "/gamma/C.java", 1),
            edge(ROOT + "/gamma/C.java", ROOT + "/alpha/A.java", 1),
            edge(ROOT + "/gamma/C.java", ROOT + "/beta/B.java", 2));

    final List<GraphMatch> matches = rule().matches(graph);

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().match().message())
        .isEqualTo("slices form a cycle: alpha -> beta -> gamma -> alpha");
    assertThat(matches.getFirst().file()).isEqualTo(ROOT + "/alpha/A.java");
  }

  @Test
  void reportsTheSameFindingsWhateverTheEdgeOrder() {
    final List<DependencyEdge> edges = new ArrayList<>();
    edges.add(edge(ROOT + "/orders/Order.java", ROOT + "/billing/Invoice.java", 3));
    edges.add(edge(ROOT + "/billing/Invoice.java", ROOT + "/orders/Order.java", 5));
    final List<GraphMatch> forward = rule().matches(new DependencyGraph(edges));
    Collections.reverse(edges);

    assertThat(rule().matches(new DependencyGraph(edges))).isEqualTo(forward);
  }

  @Test
  void findsALongChainOfSlicesWithoutRecursingDeeply() {
    final int count = 3000;
    final List<DependencyEdge> edges = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      final String from = ROOT + "/s" + index + "/A.java";
      final String to = ROOT + "/s" + ((index + 1) % count) + "/A.java";
      edges.add(edge(from, to, 1));
    }

    final List<GraphMatch> matches = rule().matches(new DependencyGraph(edges));

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().match().path()).hasSize(count);
  }
}
