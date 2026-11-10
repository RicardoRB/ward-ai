package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.ProjectMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The catalog checks: unused entries, and references to catalogs the workspace does not define. */
final class NodeCatalogCheckTest {

  private static final String MESSAGE = "catalog {value}";
  private static final String WORKSPACE =
      "packages:\n  - \"packages/*\"\ncatalog:\n  react: ^18\n  lodash: ^4\n";

  @Test
  void reportsACatalogEntryNoManifestReferences() {
    final List<ProjectMatch> found =
        new NodeCatalogCheck(MESSAGE, false)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "pnpm-workspace.yaml",
                        WORKSPACE,
                        "package.json",
                        "{\"dependencies\":{\"react\":\"catalog:\"}}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("pnpm-workspace.yaml:5:3");
  }

  @Test
  void reportsACatalogReferenceToAnUndefinedEntry() {
    final List<ProjectMatch> found =
        new NodeCatalogCheck(MESSAGE, true)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "pnpm-workspace.yaml",
                        WORKSPACE,
                        "package.json",
                        "{\"dependencies\":{\"react\":\"catalog:\",\"vue\":\"catalog:\"}}")));

    assertThat(ProjectFixtures.positions(found)).containsExactly("package.json:1:37");
  }

  @Test
  void resolvesNamedCatalogsForReferences() {
    final List<ProjectMatch> found =
        new NodeCatalogCheck(MESSAGE, true)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                ProjectFixtures.manifests(
                    Map.of(
                        "pnpm-workspace.yaml",
                        "catalog:\n  react: ^18\ncatalogs:\n  tools:\n    lodash: ^4\n",
                        "package.json",
                        "{\"dependencies\":{\"react\":\"catalog:\",\"lodash\":\"catalog:tools\"}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }

  @Test
  void reportsNothingWithoutAWorkspaceFile() {
    final List<ProjectMatch> found =
        new NodeCatalogCheck(MESSAGE, false)
            .project(
                ProjectFixtures.trees(Map.of("index.ts", "export const a = 1;")),
                ProjectFixtures.manifests(Map.of("package.json", "{\"dependencies\":{}}")));

    assertThat(ProjectFixtures.positions(found)).isEmpty();
  }
}
