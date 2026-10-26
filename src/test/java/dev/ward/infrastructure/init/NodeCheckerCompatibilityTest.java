package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.CompatibilityFinding;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Declared framework checkers against the range each gate supports. */
class NodeCheckerCompatibilityTest {
  @TempDir Path root;

  @ParameterizedTest
  @CsvSource({"vue-tsc, ^2.0.0", "svelte-check, ^3.0.0", "@astrojs/check, ^0.8.0"})
  void declaredCheckerOutsideItsSupportedRangeIsIncompatible(
      final String name, final String range) {
    final List<CompatibilityFinding> findings =
        check("24", new PackageManifest(Map.of(name, range), null, null)).findings();

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.artifactId()).isEqualTo(name);
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.INCOMPATIBLE);
              assertThat(finding.message()).contains(name + " " + range);
            });
  }

  @Test
  void declaredCheckerInsideItsSupportedRangeIsCompatible() {
    assertThat(
            check("24", new PackageManifest(Map.of("vue-tsc", "^3.3.12"), null, null)).findings())
        .isEmpty();
  }

  private NodeCompatibility check(final String node, final PackageManifest manifest) {
    return NodeCompatibility.check(project(node), manifest, List.of(), false, false);
  }

  private Project project(final String node) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.NPM,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        node);
  }
}
