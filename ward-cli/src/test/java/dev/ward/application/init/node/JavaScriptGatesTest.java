package dev.ward.application.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.AgentRulesTemplate;
import dev.ward.application.init.ConfigurationTemplate;
import dev.ward.application.init.InitGeneralCatalog;
import dev.ward.application.init.InitInputs;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.node.PackageManifest;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The gates init offers a JavaScript project, and the standard preset's selection. */
class JavaScriptGatesTest {
  private static final int STANDARD_MINIMUM = 70;
  private static final PackageManifest NO_RUNNER = PackageManifest.EMPTY;

  @TempDir Path root;

  @Test
  void strictPresetAddsStrykerAndIsOffered() {
    assertThat(catalog(NO_RUNNER).presetGates(project(BuildTool.NPM), QualityPreset.STRICT))
        .containsExactly("vitest", "coverage", "stryker");
    assertThat(catalog(NO_RUNNER).presets(project(BuildTool.NPM)))
        .containsExactly(QualityPreset.STANDARD, QualityPreset.STRICT);
  }

  @Test
  void standardPresetRendersFastAndLocalProfiles() throws IOException {
    final Project project = project(BuildTool.NPM);
    final InitGeneralCatalog catalog = catalog(NO_RUNNER);
    final List<String> selected = catalog.presetGates(project, QualityPreset.STANDARD);
    final List<InitGateOption> options =
        catalog.gates(project, null).stream().filter(gate -> selected.contains(gate.id())).toList();
    final Path file = root.resolve(WardConfiguration.FILE_NAME);
    Files.writeString(file, ConfigurationTemplate.render(options, QualityPreset.STANDARD, project));

    final Map<String, GateConfiguration> gates = new TomlConfigurationReader().read(file).checks();

    assertThat(gates).containsOnlyKeys("vitest", "coverage");
    assertThat(gates.get("vitest").profiles()).isEqualTo(Set.of("pre-push", "ci"));
    assertThat(gates.get("coverage").profiles()).isEqualTo(Set.of("pre-push", "ci"));
    assertThat(gates.get("coverage").type()).isEqualTo("coverage");
    assertThat(gates.get("coverage").command()).isEmpty();
    assertThat(gates.get("coverage").minimum()).isEqualTo(STANDARD_MINIMUM);
  }

  @Test
  void sourcelessPackageIsNotSupportedButOneWithSourcesIs() {
    final InitInputs none =
        new InitInputs(new FakeEnvironmentInspection(), dir -> NO_RUNNER, dir -> false);
    final InitInputs some =
        new InitInputs(new FakeEnvironmentInspection(), dir -> NO_RUNNER, dir -> true);

    assertThat(new InitGeneralCatalog(none).isSupported(project(BuildTool.PNPM))).isFalse();
    assertThat(new InitGeneralCatalog(some).isSupported(project(BuildTool.PNPM))).isTrue();
  }

  @Test
  void noTypeCheckOrLintToolIsOfferedForJavaScript() {
    assertThat(catalog(NO_RUNNER).gates(project(BuildTool.NPM), null))
        .extracting(InitGateOption::id)
        .doesNotContain("tsc", "eslint", "biome");
  }

  @Test
  void javaScriptAgentRulesNameWardRulesAndTheRunnerOnly() {
    final Project project =
        new Project(
            root,
            Language.JAVASCRIPT,
            BuildTool.NPM,
            Framework.NONE,
            false,
            root,
            Set.of(),
            "24",
            TaskRunner.NONE,
            null);
    final InitGeneralCatalog catalog =
        new InitGeneralCatalog(
            new InitInputs(
                new FakeEnvironmentInspection("npm"), dir -> PackageManifest.EMPTY, dir -> true));
    final List<String> selected = catalog.presetGates(project, QualityPreset.STANDARD);
    final List<InitGateOption> gates =
        catalog.gates(project, null).stream().filter(gate -> selected.contains(gate.id())).toList();

    final String body =
        AgentRulesTemplate.render(project, gates, QualityPreset.STANDARD, List.of()).body();

    assertThat(body)
        .contains("ward-rules.toml", "Tests (Vitest)", "at least 70% line coverage")
        .doesNotContain("tsc", "ESLint", "Biome", "Checkstyle", "PMD", "JaCoCo", "SpotBugs");
  }

  private static InitGeneralCatalog catalog(final PackageManifest manifest) {
    return new InitGeneralCatalog(
        new InitInputs(new FakeEnvironmentInspection("npm", "pnpm"), dir -> manifest, dir -> true));
  }

  private Project project(final BuildTool manager) {
    return new Project(
        root,
        Language.JAVASCRIPT,
        manager,
        Framework.NONE,
        false,
        root,
        Set.of(),
        "24",
        TaskRunner.NONE,
        null);
  }
}
