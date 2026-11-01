package dev.ward.application.init.node;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
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

/** The gates init offers a TypeScript project, and the standard preset's selection. */
class TypeScriptGatesTest {
  private static final PackageManifest NO_RUNNER = PackageManifest.EMPTY;

  @TempDir Path root;

  @ParameterizedTest
  @CsvSource({
    "NPM, npx tsc --noEmit --pretty false",
    "PNPM, pnpm exec tsc --noEmit --pretty false",
    "YARN, yarn tsc --noEmit --pretty false",
    "BUN, bunx tsc --noEmit --pretty false"
  })
  void nodeToolsRunThroughThePackageManager(final BuildTool manager, final String tsc) {
    assertThat(String.join(" ", gate(manager, NO_RUNNER, "tsc").command())).isEqualTo(tsc);
  }

  @Test
  void pnpmAndNpmCommandsMatchTheSpecification() {
    assertThat(gate(BuildTool.PNPM, NO_RUNNER, "eslint").command())
        .containsExactly("pnpm", "exec", "eslint", ".", "--format", "json");
    assertThat(gate(BuildTool.NPM, NO_RUNNER, "prettier").command())
        .containsExactly("npx", "prettier", "--check", ".");
  }

  @Test
  void semgrepUsesTheTypeScriptRuleset() {
    assertThat(gate(BuildTool.NPM, NO_RUNNER, "semgrep").command()).contains("p/typescript");
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
        .containsExactly("tsc", gate, "prettier", "eslint");
    assertThat(catalog(manifest).gates(project(manager), null))
        .extracting(InitGateOption::id)
        .contains(gate);
  }

  @Test
  void strictPresetAddsStrykerAndIsOffered() {
    assertThat(catalog(NO_RUNNER).presetGates(project(BuildTool.NPM), QualityPreset.STRICT))
        .containsExactly("tsc", "vitest", "prettier", "eslint", "stryker", "dependency-cruiser");
    assertThat(catalog(NO_RUNNER).presets(project(BuildTool.NPM)))
        .containsExactly(QualityPreset.STANDARD, QualityPreset.STRICT);
  }

  @Test
  void standardPresetRendersFastAndLocalProfiles() throws IOException {
    final Project project = project(BuildTool.PNPM);
    final InitGeneralCatalog catalog = catalog(NO_RUNNER);
    final List<String> selected = catalog.presetGates(project, QualityPreset.STANDARD);
    final List<InitGateOption> options =
        catalog.gates(project, null).stream().filter(gate -> selected.contains(gate.id())).toList();
    final Path file = root.resolve(WardConfiguration.FILE_NAME);
    Files.writeString(file, ConfigurationTemplate.render(options, QualityPreset.STANDARD, project));

    final Map<String, GateConfiguration> gates = new TomlConfigurationReader().read(file).checks();

    assertThat(gates).containsOnlyKeys("tsc", "vitest", "prettier", "eslint");
    final Set<String> fast = Set.of("default", "pre-commit", "pre-push", "ci", "edit");
    assertThat(gates.get("eslint").profiles()).isEqualTo(fast);
    assertThat(gates.get("prettier").profiles()).isEqualTo(fast);
    assertThat(gates.get("tsc").profiles()).isEqualTo(Set.of("pre-push", "ci"));
    assertThat(gates.get("vitest").profiles()).isEqualTo(Set.of("pre-push", "ci"));
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
        new InitInputs(
            new FakeEnvironmentInspection("npm", "pnpm"), root -> manifest, root -> false));
  }

  private Project project(final BuildTool manager) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        manager,
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
