package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The partial-run table each toolchain's gates return today, pinned before the tables move behind
 * their toolchain support. A change to any of these tables is a change to what init writes.
 */
class PartialCheckSnapshotTest {
  private static final List<String> JAVA_SOURCES = List.of("src/**/*.java");
  private static final List<String> NODE_SOURCES =
      List.of(
          "**/*.ts",
          "**/*.tsx",
          "**/*.mts",
          "**/*.cts",
          "**/*.js",
          "**/*.jsx",
          "**/*.mjs",
          "**/*.cjs");
  private static final List<String> PYTHON_SOURCES = List.of("**/*.py", "**/*.pyi");
  private static final List<String> SWIFT_SOURCES = List.of("**/*.swift");
  private static final List<String> PATHS = List.of("{paths}");
  private static final List<String> WHOLE_PROJECT = List.of("**");

  private static final Project MAVEN =
      new Project(Path.of("/shop"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  private static final Project PNPM =
      new Project(
          Path.of("/shop"),
          Language.TYPESCRIPT,
          BuildTool.PNPM,
          Framework.NONE,
          false,
          false,
          Path.of("/shop"),
          Set.of(),
          "24",
          TaskRunner.NONE,
          null);
  private static final Project TURBO =
      new Project(
          Path.of("/shop"),
          Language.TYPESCRIPT,
          BuildTool.PNPM,
          Framework.NONE,
          false,
          false,
          Path.of("/shop"),
          Set.of(),
          "24",
          TaskRunner.TURBO,
          null);
  private static final Project UV =
      new Project(Path.of("/shop"), Language.PYTHON, BuildTool.UV, Framework.NONE);
  private static final Project SWIFTPM =
      new Project(Path.of("/shop"), Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);

  @Test
  void jvmMavenGatesKeepTheirTables() {
    assertThat(table("checkstyle", MAVEN, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(
                JAVA_SOURCES,
                List.of("config/checkstyle.xml", "pom.xml"),
                List.of("-Dcheckstyle.includes={paths}"),
                "**/{name}",
                ","));
    assertThat(table("pmd", MAVEN, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(
                JAVA_SOURCES, List.of("config/pmd-ruleset.xml", "pom.xml"), List.of(), null, null));
    assertThat(table("format", MAVEN, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(
                JAVA_SOURCES,
                List.of("pom.xml"),
                List.of("-DspotlessFiles={paths}"),
                ".*/{path}",
                ","));
  }

  @Test
  void nodeGatesKeepTheirTables() {
    assertThat(table("eslint", PNPM, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(
                NODE_SOURCES,
                List.of("eslint.config.*", ".eslintrc*", "package.json"),
                PATHS,
                null,
                null,
                List.of("pnpm", "exec", "eslint", "--format", "json")));
    assertThat(table("prettier", PNPM, QualityPreset.STANDARD).match()).isEqualTo(NODE_SOURCES);
  }

  @Test
  void turborepoLintKeepsItsTable() {
    final PartialCheckDefaults.Table turbo = table("eslint", TURBO, QualityPreset.STANDARD);

    assertThat(turbo.args()).containsExactly("{paths}");
    assertThat(turbo.item()).isEqualTo("--filter=...{package}");
    assertThat(turbo.command())
        .containsExactly(
            "pnpm",
            "exec",
            "turbo",
            "run",
            "lint",
            "--log-prefix=task",
            "--log-order=grouped",
            "--continue=always",
            "--ui=stream");
  }

  @Test
  void pythonGatesKeepTheirTables() {
    assertThat(table("ruff", UV, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(
                PYTHON_SOURCES,
                List.of("ruff.toml", ".ruff.toml", "pyproject.toml"),
                PATHS,
                null,
                null,
                List.of(
                    "uv", "run", "--group", "ward", "ruff", "check", "--output-format", "json")));
  }

  @Test
  void swiftGatesKeepTheirTables() {
    assertThat(table("swift-format", SWIFTPM, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(
                SWIFT_SOURCES,
                List.of(".swift-format"),
                PATHS,
                null,
                null,
                List.of("swift", "format", "lint", "--strict")));
  }

  @Test
  void neutralGatesKeepTheirTablesForEveryProject() {
    assertThat(table("semgrep", MAVEN, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(WHOLE_PROJECT, List.of(".semgrep*"), PATHS, null, null));
    assertThat(table("gitleaks", PNPM, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(
                WHOLE_PROJECT,
                List.of(".gitleaks*"),
                List.of("--no-git", "--source={paths}"),
                null,
                null));
    assertThat(PartialCheckDefaults.forOpenApi(SupportedQualityGates.OPENAPI_LINT, "api.yaml"))
        .contains(
            new PartialCheckDefaults.Table(List.of("api.yaml"), List.of(), List.of(), null, null));
  }

  private static PartialCheckDefaults.Table table(
      final String gate, final Project project, final QualityPreset preset) {
    return PartialCheckDefaults.forGate(gate, project, preset, List.of()).orElseThrow();
  }
}
