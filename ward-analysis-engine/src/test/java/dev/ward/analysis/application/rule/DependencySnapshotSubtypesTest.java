package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The related files of a unit include the files that implement a type that it can see. */
class DependencySnapshotSubtypesTest {
  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  private static SourceUnit unit(final String path, final String text) {
    return new SourceUnit(path, AnalysisLanguage.JAVA, text);
  }

  private static FileSummary summary(final String path, final String type) {
    return new FileSummary(path, List.of(), List.of(), List.of(new Symbol(type, SPAN)));
  }

  @Test
  void anImplementorOfADeclaredTypeJoinsTheClosure() {
    final Map<String, SourceUnit> units =
        Map.of(
            "Page.java",
            unit("Page.java", "class Page {}"),
            "Renderer.java",
            unit("Renderer.java", "interface Renderer {}"),
            "Html.java",
            unit("Html.java", "class Html implements Renderer<String>, Other {}"),
            "Deep.java",
            unit("Deep.java", "class Deep extends Html {}"),
            "Alone.java",
            unit("Alone.java", "class Alone {}")
        );
    final Map<String, FileSummary> summaries =
        Map.of(
            "Page.java",
            summary("Page.java", "Page"),
            "Renderer.java",
            summary("Renderer.java", "Renderer"),
            "Html.java",
            summary("Html.java", "Html"),
            "Deep.java",
            summary("Deep.java", "Deep"),
            "Alone.java",
            summary("Alone.java", "Alone")
        );
    final DependencyGraph graph =
        new DependencyGraph(
            List.of(
                new DependencyEdge(
                    "Page.java",
                    "Renderer.java",
                    new Reference("name:Renderer", SPAN)
                )
            )
        );

    final DependencySnapshot snapshot =
        new DependencySnapshot(graph, summaries, Set.of(), units);

    assertThat(snapshot.closureOf("Page.java").stream().map(SourceUnit::path))
        .containsExactly("Deep.java", "Html.java", "Renderer.java");
  }
}
