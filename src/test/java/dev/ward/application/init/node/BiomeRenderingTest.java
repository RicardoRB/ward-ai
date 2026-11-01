package dev.ward.application.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.ConfigurationTemplate;
import dev.ward.application.init.InitGeneralCatalog;
import dev.ward.application.init.InitInputs;
import dev.ward.application.init.NodeInputs;
import dev.ward.application.init.PartialCheckDefaults;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The rendered {@code ward.toml} and the partial table of a Biome project. */
class BiomeRenderingTest {
  private static final String BIOME = "biome";
  private static final PackageManifest BIOME_PACKAGE =
      new PackageManifest(Map.of("@biomejs/biome", "2.5.15"), null, null);
  private static final NodeToolConfigs NO_CONFIGS = (root, gate) -> Optional.empty();
  private static final Set<String> FAST = Set.of("default", "pre-commit", "pre-push", "ci", "edit");

  @TempDir Path root;

  @Test
  void biomeIsInEveryFastProfileOfTheRenderedConfig() throws IOException {
    final Project project = project();
    final InitGeneralCatalog catalog =
        new InitGeneralCatalog(
            InitInputs.of(
                new FakeEnvironmentInspection("npm"),
                new NodeInputs(directory -> BIOME_PACKAGE, directory -> false, NO_CONFIGS)));
    final List<String> selected = catalog.presetGates(project, QualityPreset.STANDARD);
    final List<InitGateOption> options =
        catalog.gates(project, null).stream().filter(gate -> selected.contains(gate.id())).toList();
    final Path file = root.resolve(WardConfiguration.FILE_NAME);
    Files.writeString(file, ConfigurationTemplate.render(options, QualityPreset.STANDARD, project));

    final Map<String, GateConfiguration> gates = new TomlConfigurationReader().read(file).checks();

    assertThat(gates.get(BIOME).profiles()).isEqualTo(FAST);
    assertThat(gates).doesNotContainKeys("prettier", "eslint");
  }

  @Test
  void partialBiomeCommandNarrowsToEditedFiles() {
    final PartialCheckDefaults.Table standard =
        PartialCheckDefaults.forGate(BIOME, project(), QualityPreset.STANDARD, List.of())
            .orElseThrow();
    final PartialCheckDefaults.Table strict =
        PartialCheckDefaults.forGate(BIOME, project(), QualityPreset.STRICT, List.of())
            .orElseThrow();

    assertThat(standard.command())
        .containsExactly(
            "npx",
            BIOME,
            "ci",
            "--colors=off",
            "--reporter=checkstyle",
            "--no-errors-on-unmatched");
    assertThat(standard.args()).containsExactly("{paths}");
    assertThat(standard.full()).contains("biome.jsonc", ".biome.json", "package.json");
    assertThat(strict.command()).contains("--error-on-warnings");
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
