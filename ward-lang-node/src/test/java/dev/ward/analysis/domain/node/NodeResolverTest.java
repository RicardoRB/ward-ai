package dev.ward.analysis.domain.node;

import static dev.ward.analysis.domain.node.NodeResolverFixtures.edges;
import static dev.ward.analysis.domain.node.NodeResolverFixtures.file;
import static dev.ward.analysis.domain.node.NodeResolverFixtures.layout;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.node.NodeProjectLayout.NodeTsConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NodeResolverTest {

  @Test
  void resolvesRelativeSpecifiersExtensionIndexAndJsToTs() {
    final NodeProjectLayout layout =
        layout(
            List.of("src/a.ts", "src/users.ts", "src/domain/index.ts", "src/place.ts"),
            List.of(),
            List.of());

    assertThat(
            edges(
                layout, file("src/a.ts", "static:./users", "static:./domain", "static:./place.js")))
        .containsExactly(
            "src/a.ts->src/users.ts", "src/a.ts->src/domain/index.ts", "src/a.ts->src/place.ts");
  }

  @Test
  void resolvesCommonJsRequireAndLiteralDynamicImportButNotDynamicArguments() {
    final NodeProjectLayout layout =
        layout(List.of("src/c.js", "src/d.js", "src/e.js"), List.of(), List.of());

    assertThat(edges(layout, file("src/c.js", "require:./d", "dynamic:./e", "dynamic:./missing")))
        .containsExactly("src/c.js->src/d.js", "src/c.js->src/e.js");
  }

  @Test
  void resolvesTsconfigPathsWithInheritedBaseDirectory() {
    final NodeProjectLayout layout =
        layout(
            List.of("src/application/a.ts", "src/domain/order.ts"),
            List.of(new NodeTsConfig("", null, "src", Map.of("@app/*", List.of("*")))),
            List.of());

    assertThat(edges(layout, file("src/application/a.ts", "static:@app/domain/order")))
        .containsExactly("src/application/a.ts->src/domain/order.ts");
  }

  @Test
  void ignoresBuiltInsAndExternalPackages() {
    final NodeProjectLayout layout = layout(List.of("src/a.ts"), List.of(), List.of());

    assertThat(edges(layout, file("src/a.ts", "static:react", "static:node:fs", "static:fs")))
        .isEmpty();
  }

  @Test
  void graphDoesNotDependOnSummaryOrder() {
    final NodeProjectLayout layout = layout(List.of("src/a.ts", "src/b.ts"), List.of(), List.of());
    final FileSummary first = file("src/a.ts", "static:./b");
    final FileSummary second = file("src/b.ts", "static:./a");

    assertThat(edges(layout, first, second))
        .containsExactlyInAnyOrder("src/a.ts->src/b.ts", "src/b.ts->src/a.ts");
    assertThat(edges(layout, second, first))
        .containsExactlyInAnyOrder("src/a.ts->src/b.ts", "src/b.ts->src/a.ts");
  }
}
