package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.io.IOException;
import java.nio.file.Files;
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

  @Test
  void declaredOldestSupportedBiomeHasNoFinding() {
    final PackageManifest manifest =
        new PackageManifest(Map.of("@biomejs/biome", "2.3.0"), null, null);

    assertThat(checkBiome(manifest).findings()).isEmpty();
  }

  @Test
  void declaredBiomeJustBelowTheRangeChangesNoFile() throws IOException {
    final String manifest =
        "{\n  \"devDependencies\": {\n    \"@biomejs/biome\": \"2.2.7\"\n  }\n}\n";
    Files.writeString(root.resolve("package.json"), manifest);
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final NodeBuildToolConfigurator configurator =
        new NodeBuildToolConfigurator(
            directory -> new PackageManifest(Map.of("@biomejs/biome", "2.2.7"), null, null),
            executor);
    final List<InitGateOption> biome =
        List.of(new InitGateOption("biome", "Biome", List.of("npx", "biome"), true, "ok"));

    assertThatThrownBy(() -> configurator.apply(project(), biome))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("@biomejs/biome", ">=2.3.0 <3.0.0");
    assertThat(Files.readString(root.resolve("package.json"))).isEqualTo(manifest);
    assertThat(executor.commands).isEmpty();
  }

  private NodeCompatibility checkBiome(final PackageManifest manifest) {
    return NodeCompatibility.check(project(), manifest, List.of(), Set.of("biome"));
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
        TaskRunner.NONE);
  }
}
