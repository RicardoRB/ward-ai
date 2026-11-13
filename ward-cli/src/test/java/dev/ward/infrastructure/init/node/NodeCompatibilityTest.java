package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.node.PackageManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Node and TypeScript compatibility findings, and the range forms the catalog uses. */
class NodeCompatibilityTest {
  @TempDir Path root;

  @ParameterizedTest
  @CsvSource({
    "'^22.13.0', 22.13.0, true",
    "'^22.13.0', 22.12.9, false",
    "'^22.13.0', 23.0.0, false",
    "'>=4.8.4 <6.1.0', 6.0.3, true",
    "'>=4.8.4 <6.1.0', 7.0.2, false",
    "'>=4.8.4 <6.1.0', 4.8.3, false",
    "'^18.18.0 || ^20.9.0 || >=21.1.0', 20.9.0, true",
    "'^18.18.0 || ^20.9.0 || >=21.1.0', 19.0.0, false",
    "'5.9.3', 5.9.3, true",
    "'5.9.3', 5.9.4, false"
  })
  void matchesTheCatalogsRangeForms(final String range, final String version, final boolean ok) {
    assertThat(NodeVersionRange.matches(range, version)).isEqualTo(ok);
  }

  @Test
  void readsTheLowerBoundOfADeclaredRange() {
    assertThat(NodeVersionRange.lowerBound("~5.9.3")).contains("5.9.3");
    assertThat(NodeVersionRange.lowerBound("^7")).contains("7.0.0");
    assertThat(NodeVersionRange.lowerBound("latest")).isEmpty();
  }

  @Test
  void nodeTooOldForVitestIsIncompatible() {
    final List<CompatibilityFinding> findings =
        check("18", PackageManifest.EMPTY, "vitest", "@vitest/coverage-v8").findings();

    assertThat(findings)
        .extracting(CompatibilityFinding::artifactId)
        .containsExactly("vitest", "@vitest/coverage-v8");
    assertThat(findings)
        .allSatisfy(
            finding -> {
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.INCOMPATIBLE);
              assertThat(finding.message()).contains("^20.0.0 || ^22.0.0 || >=24.0.0", "Node 18");
            });
  }

  @Test
  void compatibleProjectHasNoFindings() {
    final PackageManifest manifest = new PackageManifest(Map.of("typescript", "5.9.3"), null, null);

    assertThat(check("24", manifest, "@vitest/coverage-v8", "vitest").findings()).isEmpty();
  }

  private NodeCompatibility check(
      final String node, final PackageManifest manifest, final String... packages) {
    return NodeCompatibility.check(
        project(node),
        manifest,
        List.of(packages).stream()
            .map(name -> NodePackageCatalog.find(name).orElseThrow())
            .toList(),
        Set.of());
  }

  private Project project(final String node) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.NPM,
        Framework.NONE,
        false,
        root,
        Set.of(),
        node,
        TaskRunner.NONE,
        null);
  }
}
