package dev.ward.analysis.domain.rule;

import static dev.ward.analysis.domain.rule.GraphRuleFixtures.definition;
import static dev.ward.analysis.domain.rule.GraphRuleFixtures.edge;
import static dev.ward.analysis.domain.rule.GraphRuleFixtures.graph;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.Reference;
import org.junit.jupiter.api.Test;

class CyclicImportRuleTest {

  private static final String PY_A = "pkg/a.py";
  private static final String PY_B = "pkg/b.py";
  private static final String JAVA_A = "src/main/java/com/acme/A.java";
  private static final String JAVA_B = "src/main/java/com/acme/B.java";
  private static final String TS_A = "web/a.ts";
  private static final String TS_B = "web/b.ts";

  private static CyclicImportRule rule() {
    return new CyclicImportRule(definition(CyclicImportRule.ID, "check = \"cyclic-import\""));
  }

  @Test
  void reportsAPythonCycleWithItsDottedLabels() {
    final DependencyGraph graph =
        graph(edge(PY_A, PY_B, 1), edge(PY_B, PY_A, 2));

    final var matches = rule().matches(graph);

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().file()).isEqualTo(PY_A);
    assertThat(matches.getFirst().match().message()).isEqualTo("Cyclic import (pkg.a -> pkg.b)");
  }

  @Test
  void aJavaCycleInTheSameGraphIsNotReported() {
    assertThat(rule().matches(graph(edge(JAVA_A, JAVA_B, 1), edge(JAVA_B, JAVA_A, 2)))).isEmpty();
  }

  @Test
  void aTypeScriptCycleInTheSameGraphIsNotReported() {
    assertThat(rule().matches(graph(edge(TS_A, TS_B, 1), edge(TS_B, TS_A, 2)))).isEmpty();
  }

  @Test
  void aJavaCycleDoesNotHideAPythonCycleInTheSameGraph() {
    final DependencyGraph graph =
        graph(
            edge(JAVA_A, JAVA_B, 1),
            edge(JAVA_B, JAVA_A, 2),
            edge(PY_A, PY_B, 3),
            edge(PY_B, PY_A, 4));

    final var matches = rule().matches(graph);

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().file()).isEqualTo(PY_A);
  }

  @Test
  void aTypeCheckingEdgeIsNotCounted() {
    final DependencyEdge runtime = edge(PY_A, PY_B, 1);
    final DependencyEdge guarded = edge(PY_B, PY_A, 2);
    final DependencyEdge typeOnly =
        new DependencyEdge(
            guarded.from(),
            guarded.to(),
            new Reference(guarded.reference().specifier(), guarded.reference().span(), true));

    assertThat(rule().matches(graph(runtime, typeOnly))).isEmpty();
  }
}
