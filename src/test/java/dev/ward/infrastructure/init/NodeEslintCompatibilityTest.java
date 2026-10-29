package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A declared ESLint must sit inside the range the generated config supports. */
class NodeEslintCompatibilityTest {
  private static final String ESLINT = "eslint";
  @TempDir Path root;

  @Test
  void declaredEslintBelowTheSupportedRangeIsIncompatible() {
    final PackageManifest manifest = new PackageManifest(Map.of(ESLINT, "8.57.0"), null, null);

    assertThat(checkEslint(manifest).findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.artifactId()).isEqualTo(ESLINT);
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.INCOMPATIBLE);
              assertThat(finding.message()).contains("8.57.0", ">=9.22.0 <11.0.0");
            });
  }

  @Test
  void declaredEslintInsideTheSupportedRangeHasNoFinding() {
    final PackageManifest manifest = new PackageManifest(Map.of(ESLINT, "9.39.0"), null, null);

    assertThat(checkEslint(manifest).findings()).isEmpty();
  }

  @Test
  void declaredCaretEslintWithASupportedMajorHasNoFinding() {
    final PackageManifest manifest = new PackageManifest(Map.of(ESLINT, "^9"), null, null);

    assertThat(checkEslint(manifest).findings()).isEmpty();
  }

  @Test
  void declaredCaretEslintWithAnUnsupportedMajorIsIncompatible() {
    final PackageManifest manifest = new PackageManifest(Map.of(ESLINT, "^8.57.0"), null, null);

    assertThat(checkEslint(manifest).findings()).singleElement();
  }

  @Test
  void declaredExactEslintBelowTheRangeInsideItsMajorIsIncompatible() {
    final PackageManifest manifest = new PackageManifest(Map.of(ESLINT, "9.20.0"), null, null);

    assertThat(checkEslint(manifest).findings()).singleElement();
  }

  @Test
  void declaredEslintBelowTheRangeChangesNoFile() throws IOException {
    final String manifest = "{\n  \"devDependencies\": {\n    \"eslint\": \"8.57.0\"\n  }\n}\n";
    Files.writeString(root.resolve("package.json"), manifest);
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final NodeBuildToolConfigurator configurator =
        new NodeBuildToolConfigurator(
            directory -> new PackageManifest(Map.of(ESLINT, "8.57.0"), null, null), executor);
    final List<InitGateOption> eslint =
        List.of(new InitGateOption(ESLINT, "ESLint", List.of("npx", ESLINT), true, "ok"));

    final PlannedFileChange plan = configurator.plan(project(), eslint, null, false);

    assertThat(plan.action()).isEqualTo(PlannedFileChange.Action.CONFLICT);
    assertThat(plan.details())
        .singleElement()
        .asString()
        .contains("INCOMPATIBLE eslint", ">=9.22.0 <11.0.0");
    assertThatThrownBy(() -> configurator.apply(project(), eslint))
        .isInstanceOf(WardException.class)
        .hasMessageContaining(ESLINT);
    assertThat(Files.readString(root.resolve("package.json"))).isEqualTo(manifest);
    assertThat(executor.commands).isEmpty();
  }

  private NodeCompatibility checkEslint(final PackageManifest manifest) {
    return NodeCompatibility.check(project(), manifest, List.of(), Set.of("eslint"));
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
