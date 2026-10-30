package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.project.FileSystemNodeToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The dependency-cruiser gate init offers Node projects, and the config it reads. */
class NodeDependencyCruiserGatesTest {
  private static final PackageManifest NO_RUNNER = PackageManifest.EMPTY;
  private static final String GATE = "dependency-cruiser";

  @TempDir Path root;

  @ParameterizedTest
  @CsvSource({
    "NPM, npx depcruise . --config .dependency-cruiser.mjs --output-type err",
    "PNPM, pnpm exec depcruise . --config .dependency-cruiser.mjs --output-type err",
    "YARN, yarn depcruise . --config .dependency-cruiser.mjs --output-type err",
    "BUN, bunx depcruise . --config .dependency-cruiser.mjs --output-type err"
  })
  void typeScriptGateRunsThroughThePackageManager(final BuildTool manager, final String command) {
    assertThat(String.join(" ", gate(Language.TYPESCRIPT, manager).command())).isEqualTo(command);
  }

  @Test
  void javaScriptGateRunsThroughThePackageManager() {
    assertThat(gate(Language.JAVASCRIPT, BuildTool.BUN).command())
        .containsExactly(
            "bunx",
            "depcruise",
            ".",
            "--config",
            ".dependency-cruiser.mjs",
            "--output-type",
            "err");
  }

  @Test
  void projectsOwnConfigIsPassedToTheGate() throws IOException {
    Files.writeString(root.resolve(".dependency-cruiser.cjs"), "module.exports = {};\n");
    final InitGeneralCatalog catalog =
        new InitGeneralCatalog(
            new InitInputs(
                new FakeEnvironmentInspection("npm"),
                dir -> NO_RUNNER,
                dir -> false,
                new FileSystemNodeToolConfigs()));

    assertThat(
            catalog.gates(project(Language.TYPESCRIPT, BuildTool.NPM), null).stream()
                .filter(gate -> GATE.equals(gate.id()))
                .findFirst()
                .orElseThrow()
                .command())
        .containsExactly(
            "npx", "depcruise", ".", "--config", ".dependency-cruiser.cjs", "--output-type", "err");
  }

  @Test
  void gateIsOfferedButLeftOutOfTheStandardPreset() {
    final InitGeneralCatalog catalog = catalog(NO_RUNNER);
    final Project project = project(Language.TYPESCRIPT, BuildTool.NPM);

    assertThat(catalog.gates(project, null)).extracting(InitGateOption::id).contains(GATE);
    assertThat(catalog.presetGates(project, QualityPreset.STANDARD)).doesNotContain(GATE);
  }

  @Test
  void strictPresetRunsTheGateInPrePushAndCi() throws IOException {
    final Project project = project(Language.TYPESCRIPT, BuildTool.NPM);
    final InitGeneralCatalog catalog = catalog(NO_RUNNER);
    final List<String> selected = catalog.presetGates(project, QualityPreset.STRICT);
    final List<InitGateOption> options =
        catalog.gates(project, null).stream().filter(gate -> selected.contains(gate.id())).toList();
    final Path file = root.resolve(WardConfiguration.FILE_NAME);
    Files.writeString(file, ConfigurationTemplate.render(options, QualityPreset.STRICT, project));

    final Map<String, GateConfiguration> gates = new TomlConfigurationReader().read(file).checks();

    assertThat(gates.get(GATE).profiles()).isEqualTo(Set.of("pre-push", "ci"));
    assertThat(gates.get(GATE).command())
        .containsExactly(
            "npx", "depcruise", ".", "--config", ".dependency-cruiser.mjs", "--output-type", "err");
  }

  @Test
  void strictTypeScriptAgentRulesNameDependencyCruiserAndNoArchUnit() {
    final Project project = project(Language.TYPESCRIPT, BuildTool.NPM);
    final InitGeneralCatalog catalog = catalog(NO_RUNNER);
    final List<String> selected = catalog.presetGates(project, QualityPreset.STRICT);
    final List<InitGateOption> gates =
        catalog.gates(project, null).stream().filter(gate -> selected.contains(gate.id())).toList();

    final String body =
        AgentRulesTemplate.render(project, gates, QualityPreset.STRICT, List.of()).body();

    assertThat(body)
        .contains("Dependency rules (dependency-cruiser)", "do not edit or weaken")
        .doesNotContain("ArchUnit");
  }

  private InitGateOption gate(final Language language, final BuildTool manager) {
    return catalog(NO_RUNNER).gates(project(language, manager), null).stream()
        .filter(gate -> GATE.equals(gate.id()))
        .findFirst()
        .orElseThrow();
  }

  private InitGeneralCatalog catalog(final PackageManifest manifest) {
    return new InitGeneralCatalog(
        new InitInputs(
            new FakeEnvironmentInspection("npm", "pnpm"), dir -> manifest, dir -> false));
  }

  private Project project(final Language language, final BuildTool manager) {
    return new Project(
        root,
        language,
        manager,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        "24",
        TaskRunner.NONE);
  }
}
