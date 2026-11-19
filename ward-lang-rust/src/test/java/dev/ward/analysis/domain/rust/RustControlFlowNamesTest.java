package dev.ward.analysis.domain.rust;

import static dev.ward.analysis.domain.rust.RustFlowFixtures.accesses;
import static dev.ward.analysis.domain.rust.RustFlowFixtures.graph;
import static dev.ward.analysis.domain.rust.RustFlowFixtures.names;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

class RustControlFlowNamesTest {
  private static final String ONE_PARAMETER = "f(1)";

  @Test
  void everyFunctionWithABodyIsAGraphNamedByItsOwnerAndArity() {
    final String source =
        """
        mod geometry {
            pub fn free(a: u8, b: u8) {}
            struct Circle;
            impl<T> Shape for Circle {
                fn area(&self, scale: f64) -> f64 { 1.0 }
            }
            trait Shape {
                fn name(&self) -> String { String::new() }
                fn missing(&self);
            }
        }
        impl Circle { fn new() -> Self { Circle } }
        """;

    assertThat(names(source))
        .containsExactly(
            "geometry#free(2)",
            "geometry::Circle#area(1)",
            "geometry::Shape#name(0)",
            "Circle#new(0)"
        );
  }

  @Test
  void aNestedFunctionIsAGraphOfItsOwnAndItsNamesStayOutOfTheOuterBody() {
    final String source =
        """
        fn outer(x: u32) -> u32 {
            fn inner(y: u32) -> u32 { y }
            x
        }
        """;

    assertThat(names(source)).containsExactlyInAnyOrder("outer(1)", "inner(1)");
    assertThat(accesses(graph(source, "outer(1)"))).noneMatch(access -> "y".equals(access.name()));
  }

  @Test
  void aFunctionWithoutABodyAddsNoGraph() {
    assertThat(names("trait T { fn f(&self); } extern \"C\" { fn g(); }")).isEmpty();
  }

  @Test
  void aWriteIsOrderedAfterTheReadsOfItsValueAndTheParametersComeFirst() {
    final ControlFlowGraph graph = graph(
        "fn f(x: u8) { let x = x + 1; let _ = x; }",
        ONE_PARAMETER
    );
    final List<String> order =
        graph.blocks().get(graph.entry()).accesses().stream()
            .map(access -> access.kind() + ":" + access.name())
            .toList();

    assertThat(order)
        .containsSubsequence(
            AccessKind.PARAMETER + ":x",
            AccessKind.READ + ":x",
            AccessKind.WRITE + ":x",
            AccessKind.READ + ":x"
        );
  }

  @Test
  void aMethodReceiverIsAParameterAtTheEntryButNotPartOfTheArity() {
    final ControlFlowGraph graph = graph("impl S { fn f(&self, n: u8) -> u8 { n } }", "S#f(1)");

    assertThat(graph.blocks().get(graph.entry()).accesses())
        .anyMatch(access -> "self".equals(access.name()) && access.kind() == AccessKind.PARAMETER);
  }
}
