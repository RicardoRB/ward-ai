package dev.ward.analysis.domain.graph;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyGraphTest {
  private static final String A_FILE = "A.java";
  private static final String B_FILE = "B.java";
  private static final String C_FILE = "C.java";
  private static final Span AT_1 = new Span(1, 1, 1, 9, 0, 8);
  private static final DependencyEdge B_TO_A =
      new DependencyEdge(B_FILE, A_FILE, new Reference("A", AT_1));
  private static final DependencyEdge C_TO_A =
      new DependencyEdge(C_FILE, A_FILE, new Reference("A", AT_1));
  private static final DependencyEdge C_TO_B =
      new DependencyEdge(C_FILE, B_FILE, new Reference("B", AT_1));

  @Test
  void reverseEdgesMirrorForwardEdges() {
    final DependencyGraph graph = new DependencyGraph(List.of(B_TO_A, C_TO_A, C_TO_B));

    assertThat(graph.dependenciesOf(C_FILE)).containsExactly(A_FILE, B_FILE);
    assertThat(graph.dependentsOf(A_FILE)).containsExactly(B_FILE, C_FILE);
    assertThat(graph.dependentsOf(B_FILE)).containsExactly(C_FILE);
    assertThat(graph.dependenciesOf(A_FILE)).isEmpty();
  }

  @Test
  void inputOrderDoesNotChangeTheGraph() {
    final DependencyGraph forward = new DependencyGraph(List.of(B_TO_A, C_TO_A, C_TO_B));
    final DependencyGraph reversed = new DependencyGraph(List.of(C_TO_B, C_TO_A, B_TO_A));

    assertThat(reversed).isEqualTo(forward);
    assertThat(reversed.hashCode()).isEqualTo(forward.hashCode());
    assertThat(reversed.edges()).containsExactlyElementsOf(forward.edges());
  }

  @Test
  void graphWithoutEdgesHasNoNeighbours() {
    final DependencyGraph graph = new DependencyGraph(List.of());

    assertThat(graph.edges()).isEmpty();
    assertThat(graph.dependentsOf(A_FILE)).isEmpty();
    assertThat(graph).isNotEqualTo(new DependencyGraph(List.of(B_TO_A)));
  }

  @Test
  void closureFollowsDependenciesThroughOtherFilesAndSurvivesCycles() {
    final DependencyEdge aToC = new DependencyEdge(A_FILE, C_FILE, new Reference("C", AT_1));
    final DependencyGraph chain = new DependencyGraph(List.of(B_TO_A, C_TO_B));
    final DependencyGraph cycle = new DependencyGraph(List.of(B_TO_A, C_TO_B, aToC));

    assertThat(chain.closureOf(C_FILE)).containsExactly(A_FILE, B_FILE);
    assertThat(chain.closureOf(A_FILE)).isEmpty();
    assertThat(cycle.closureOf(A_FILE)).containsExactly(B_FILE, C_FILE);
  }
}
