package dev.ward.analysis.domain.node;

import static dev.ward.analysis.domain.node.NodeResolverFixtures.edges;
import static dev.ward.analysis.domain.node.NodeResolverFixtures.file;
import static dev.ward.analysis.domain.node.NodeResolverFixtures.layout;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.node.NodeProjectLayout.ExportEntry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Resolution of workspace packages through their {@code exports} and conditions. */
class NodePackageResolutionTest {

  @Test
  void resolvesWorkspacePackagesThroughExportsWithConditions() {
    final NodeProjectLayout layout =
        layout(
            List.of(
                "apps/web/src/index.ts", "packages/ui/src/index.ts", "packages/ui/src/button.tsx"),
            List.of(),
            List.of(
                NodeResolverFixtures.pkg(
                    "@repo/ui",
                    "packages/ui",
                    Map.of(
                        ".",
                        List.of(new ExportEntry(List.of(), "./src/index.ts")),
                        "./button",
                        List.of(new ExportEntry(List.of(), "./src/button.tsx")),
                        "./*",
                        List.of(new ExportEntry(List.of(), "./src/*.tsx"))))));

    assertThat(
            edges(
                layout, file("apps/web/src/index.ts", "static:@repo/ui", "static:@repo/ui/button")))
        .containsExactly(
            "apps/web/src/index.ts->packages/ui/src/index.ts",
            "apps/web/src/index.ts->packages/ui/src/button.tsx");
  }

  @Test
  void selectsTypesThenImportForStaticAndRequireForCommonJs() {
    final NodeProjectLayout layout =
        layout(
            List.of("lib/types.d.ts", "lib/esm.js", "cjs/cjs.cjs", "app/a.ts"),
            List.of(),
            List.of(
                NodeResolverFixtures.pkg(
                    "lib",
                    "lib",
                    Map.of(
                        ".",
                        List.of(
                            new ExportEntry(List.of("types"), "./types.d.ts"),
                            new ExportEntry(List.of("import"), "./esm.js")))),
                NodeResolverFixtures.pkg(
                    "cjs",
                    "cjs",
                    Map.of(".", List.of(new ExportEntry(List.of("require"), "./cjs.cjs"))))));

    assertThat(edges(layout, file("app/a.ts", "static:lib", "require:cjs")))
        .containsExactly("app/a.ts->lib/types.d.ts", "app/a.ts->cjs/cjs.cjs");
  }
}
