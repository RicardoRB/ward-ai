package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Vitest a project runs must support the project's Vite, as Vitest's own range declares. */
class NodeVitestCompatibilityTest {
  private static final String VITEST = NodePackageCatalog.VITEST;
  private static final String VITE = NodePackageCatalog.VITE;
  @TempDir Path root;

  @Test
  void declaredVitestThatDoesNotSupportTheDeclaredViteIsIncompatible() {
    final PackageManifest manifest =
        new PackageManifest(Map.of(VITEST, "4.0.18", VITE, "^8.2.2"), null, null);

    assertThat(check(manifest, VITEST).findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.artifactId()).isEqualTo(VITEST);
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.INCOMPATIBLE);
              assertThat(finding.message())
                  .contains("vitest 4.0.18", "Vite 8.2.2", "^6.0.0 || ^7.0.0");
            });
  }

  @Test
  void undeclaredVitestWithAViteNoPinnedReleaseSupportsIsIncompatible() {
    final PackageManifest manifest = new PackageManifest(Map.of(VITE, "^9.0.0"), null, null);

    assertThat(check(manifest, VITEST).findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.artifactId()).isEqualTo(VITEST);
              assertThat(finding.message())
                  .contains("No pinned vitest supports Vite 9.0.0", "^6.0.0 || ^7.0.0 || ^8.0.0");
            });
  }

  @Test
  void declaredVitestThatSupportsTheDeclaredViteHasNoFinding() {
    final PackageManifest manifest =
        new PackageManifest(Map.of(VITEST, "4.1.11", VITE, "^8.2.2"), null, null);

    assertThat(check(manifest, VITEST).findings()).isEmpty();
  }

  @Test
  void declaredVitest5WithAViteHasNoViteFinding() {
    final PackageManifest manifest =
        new PackageManifest(Map.of(VITEST, "5.0.0", VITE, "^8.2.2"), null, null);

    assertThat(check(manifest, VITEST).findings()).isEmpty();
  }

  @Test
  void projectWithoutViteHasNoViteFinding() {
    final PackageManifest manifest = new PackageManifest(Map.of(VITEST, "4.0.18"), null, null);

    assertThat(check(manifest, VITEST).findings()).isEmpty();
  }

  @Test
  void viteIsNotCheckedWhenTheVitestGateIsNotSelected() {
    final PackageManifest manifest =
        new PackageManifest(Map.of(VITEST, "4.0.18", VITE, "^8.2.2"), null, null);

    assertThat(check(manifest, "jest").findings()).isEmpty();
  }

  private NodeCompatibility check(final PackageManifest manifest, final String gate) {
    return NodeCompatibility.check(project(), manifest, List.of(), Set.of(gate));
  }

  private Project project() {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.NPM,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        "24",
        TaskRunner.NONE,
        null);
  }
}
