package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.port.ControlFlowResult;
import org.junit.jupiter.api.Test;

class JavaBodyNamesTest {

  @Test
  void bodiesOfAPackagedTypeAreNamedWithTheirPackage() {
    final ControlFlowResult result =
        JavaFlowFixtures.lower("package shop;\nclass A { class B { void m() { } } void n() { } }");

    assertThat(result.graphs().stream().map(ControlFlowGraph::body).toList())
        .containsExactlyInAnyOrder("shop:A#n(0)", "shop:A.B#m(0)");
  }

  @Test
  void bodiesOfATypeWithoutAPackageAreNamedByTheirTypes() {
    final ControlFlowResult result =
        JavaFlowFixtures.lower("class A { class B { void m() { } } void n() { } }");

    assertThat(result.graphs().stream().map(ControlFlowGraph::body).toList())
        .containsExactlyInAnyOrder("A#n(0)", "A.B#m(0)");
  }
}
