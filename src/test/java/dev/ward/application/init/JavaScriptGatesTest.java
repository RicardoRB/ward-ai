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
import dev.ward.infrastructure.config.TomlConfigurationReader;
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

/** The gates init offers a JavaScript project, and the standard preset's selection. */
class JavaScriptGatesTest {
  private static final PackageManifest NO_RUNNER = PackageManifest.EMPTY;
  private static final String ESLINT = "eslint";

  @TempDir Path root;

  @Test
  void npmAndBunCommandsRunThroughThePackageManager() {
    assertThat(gate(BuildTool.NPM, NO_RUNNER, ESLINT).command())
        .containsExactly("npx", ESLINT, ".", "--format", "json");
    assertThat(gate(BuildTool.NPM, NO_RUNNER, "prettier").command())
        .containsExactly("npx", "prettier", "--check", ".");
    assertThat(gate(BuildTool.BUN, NO_RUNNER, ESLINT).command())
        .containsExactly("bunx", ESLINT, ".", "--format", "json");
  }

  @Test
  void noTypeCheckIsOfferedAndSemgrepUsesTheJavaScriptRuleset() {
    assertThat(catalog(NO_RUNNER).gates(project(BuildTool.NPM), null))
        .extracting(InitGateOption::id)
        .doesNotContain("tsc");
    assertThat(gate(BuildTool.NPM, NO_RUNNER, "semgrep").command()).contains("p/javascript");
  }

  @ParameterizedTest
  @CsvSource({
    "NPM, vitest, vitest",
    "NPM, jest, jest",
    "BUN, jest, jest",
    "BUN, '', bun-test",
    "PNPM, '', vitest"
  })
  void declaredRunnerIsReusedAndVitestIsTheDefault(
      final BuildTool manager, final String declared, final String gate) {
    final PackageManifest manifest =
        declared.isEmpty()
            ? NO_RUNNER
            : new PackageManifest(Map.of(declared, "^1.0.0"), null, null);

    assertThat(catalog(manifest).presetGates(project(manager), QualityPreset.STANDARD))
        .containsExactly(gate, "prettier", ESLINT);
  }

  @Test
  void strictPresetAddsStrykerAndIsOffered() {
    assertThat(catalog(NO_RUNNER).presetGates(project(BuildTool.NPM), QualityPreset.STRICT))
        .containsExactly("vitest", "prettier", ESLINT, "stryker", "dependency-cruiser");
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

    final Map<String, GateConfiguration> gates = new TomlConfigurationReader().read(file).gates();

    assertThat(gates).containsOnlyKeys("vitest", "prettier", ESLINT);
    final Set<String> fast = Set.of("default", "pre-commit", "pre-push", "ci");
    assertThat(gates.get(ESLINT).profiles()).isEqualTo(fast);
    assertThat(gates.get("prettier").profiles()).isEqualTo(fast);
    assertThat(gates.get("vitest").profiles()).isEqualTo(Set.of("pre-push", "ci"));
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
  void javaScriptAgentRulesNameEslintPrettierAndTheRunnerOnly() {
    final Project project =
        new Project(
            root,
            Language.JAVASCRIPT,
            BuildTool.NPM,
            Framework.NONE,
            false,
            false,
            root,
            Set.of(),
            "24");
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
        .contains(
            "ESLint's recommended JavaScript rules",
            "Prettier",
            "Tests (Vitest)",
            "at least 70% line coverage")
        .doesNotContain(
            "tsc", "TypeScript", "typescript-eslint", "Checkstyle", "PMD", "JaCoCo", "SpotBugs");
  }

  private InitGateOption gate(
      final BuildTool manager, final PackageManifest manifest, final String id) {
    return catalog(manifest).gates(project(manager), null).stream()
        .filter(gate -> gate.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private static InitGeneralCatalog catalog(final PackageManifest manifest) {
    return new InitGeneralCatalog(
        new InitInputs(new FakeEnvironmentInspection("npm", "pnpm"), dir -> manifest, dir -> true));
  }

  private Project project(final BuildTool manager) {
    return new Project(
        root, Language.JAVASCRIPT, manager, Framework.NONE, false, false, root, Set.of(), "24");
  }
}
