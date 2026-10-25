package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A declared Biome must sit inside the range Ward's checkstyle reading supports. */
class NodeBiomeCompatibilityTest {
  @TempDir Path root;

  @Test
  void declaredBiomeBelowTheSupportedRangeIsIncompatible() {
    final PackageManifest manifest =
        new PackageManifest(Map.of("@biomejs/biome", "^2.2.0"), null, null);

    assertThat(checkBiome(manifest).findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.artifactId()).isEqualTo("@biomejs/biome");
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.INCOMPATIBLE);
              assertThat(finding.message()).contains(">=2.3.0 <3.0.0");
            });
  }

  @Test
  void declaredBiomeInsideTheSupportedRangeHasNoFinding() {
    final PackageManifest manifest =
        new PackageManifest(Map.of("@biomejs/biome", "^2.6.0"), null, null);

    assertThat(checkBiome(manifest).findings()).isEmpty();
  }

  @Test
  void declaredLatestBiomeHasNoFinding() {
    final PackageManifest manifest =
        new PackageManifest(Map.of("@biomejs/biome", "latest"), null, null);

    assertThat(checkBiome(manifest).findings()).isEmpty();
  }

  private NodeCompatibility checkBiome(final PackageManifest manifest) {
    return NodeCompatibility.check(project(), manifest, List.of(), true);
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
        "24");
  }
}
