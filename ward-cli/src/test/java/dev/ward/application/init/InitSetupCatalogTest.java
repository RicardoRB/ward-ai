package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.JavaPresetGates;
import dev.ward.ProjectFixtures;
import dev.ward.application.gate.QualityGateFactory;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitIntegrationOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitSetupCatalogTest {
  private static final Set<String> NODE_GATES =
      Set.of(
          "tsc",
          "vitest",
          "jest",
          "bun-test",
          "stryker",
          "vue-tsc",
          "svelte-check",
          "astro-check",
          "ngc",
          "ng-test");

  /** Python gates are offered by init only for Python projects (#136). */
  private static final Set<String> PYTHON_GATES =
      Set.of("mypy", "pytest", "mutmut", "django-check");

  private static final Set<String> ANDROID_GATES =
      Set.of(
          SupportedQualityGates.ANDROID_LINT,
          SupportedQualityGates.ANDROID_UNIT_TEST,
          SupportedQualityGates.ANDROID_COVERAGE);
  private static final Set<String> SWIFT_GATES =
      Set.of("harmonize", "swift-build", "swift-test", "xcodebuild-test", "swift-coverage");

  private static final double STRICT_LINE_MINIMUM = 0.85;
  private static final double STRICT_BRANCH_MINIMUM = 0.75;
  private static final int STRICT_MUTATION_THRESHOLD = 60;
  private static final String COMPILE = "compile";
  private static final String TESTS = "tests";

  @Test
  void exposesStableIntegrationAndGateChoices(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());

    assertThat(catalog.integrations())
        .extracting(InitIntegrationOption::id)
        .containsExactly(
            "none",
            "opencode",
            "claude-code",
            "codex",
            "cursor",
            "copilot",
            "gemini",
            "windsurf",
            "cline",
            "factory-droid",
            "amp",
            "junie");
    assertThat(
            catalog.gates(
                new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow(),
                null))
        .allSatisfy(gate -> assertThat(gate.description()).isNotBlank());
  }

  @Test
  void exposesPresetRules(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());
    final Project maven =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
    assertThat(catalog.presets(maven))
        .containsExactly(QualityPreset.STANDARD, QualityPreset.STRICT);
    assertThat(JavaPresetGates.STANDARD).containsExactly(COMPILE, TESTS, "coverage");
    assertThat(QualityPreset.STRICT.rules().jacocoMinimums())
        .containsEntry("LINE", STRICT_LINE_MINIMUM)
        .containsEntry("BRANCH", STRICT_BRANCH_MINIMUM);
    assertThat(QualityPreset.STRICT.rules().enforcerRules())
        .containsExactly("dependencyConvergence", "requireUpperBoundDeps");
    assertThat(QualityPreset.STANDARD.rules().enforcerRules())
        .containsExactly("dependencyConvergence");
    assertThat(QualityPreset.STRICT.rules().mutationThreshold())
        .isEqualTo(STRICT_MUTATION_THRESHOLD);
    assertThat(QualityPreset.STRICT.rules().architectureCycles()).isTrue();
    assertThatThrownBy(() -> catalog.preset("paranoid")).hasMessageContaining("standard", "strict");
  }

  @Test
  void prefersWrapperWhenSystemMavenIsMissing(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    Files.writeString(dir.resolve("mvnw"), "#!/bin/sh\nexit 0\n");
    final InitGateOption option =
        new InitSetupCatalog(new FakeEnvironmentInspection())
            .gate(
                new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow(),
                TESTS);

    assertThat(option.command()).containsExactly("./mvnw", "test");
    assertThat(option.available()).isTrue();
    assertThat(option.availabilityMessage()).contains("Maven command available: ./mvnw");
  }

  @Test
  void rejectsUnsupportedChoices(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());

    assertThatThrownBy(() -> catalog.integration("unknown")).isInstanceOf(WardException.class);
    assertThatThrownBy(() -> catalog.gate(project, "unknown")).isInstanceOf(WardException.class);
  }

  @Test
  void reportsUnavailableMavenWhenWrapperAndSystemToolAreMissing(@TempDir final Path dir)
      throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    final InitGateOption option =
        new InitSetupCatalog(new FakeEnvironmentInspection()).gate(project, TESTS);

    assertThat(option.available()).isFalse();
    assertThat(option.availabilityMessage()).contains("Maven is unavailable");
  }

  @Test
  void reportsSystemMavenWhenWrapperIsMissing(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    final InitGateOption option =
        new InitSetupCatalog(new FakeEnvironmentInspection("mvn")).gate(project, TESTS);

    assertThat(option.available()).isTrue();
    assertThat(option.availabilityMessage()).contains("Maven command available: mvn");
  }

  @Test
  void usesGradleWrapperAndGradleTasks(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle"), "plugins { id 'java' }\n");
    Files.writeString(dir.resolve("gradlew"), "#!/bin/sh\nexit 0\n");
    Files.setPosixFilePermissions(
        dir.resolve("gradlew"), PosixFilePermissions.fromString("rwxr-xr-x"));
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());

    assertThat(catalog.gate(project, TESTS).command()).containsExactly("./gradlew", "test");
    assertThat(catalog.gate(project, COMPILE).command()).containsExactly("./gradlew", "classes");
    assertThat(catalog.gate(project, TESTS).availabilityMessage())
        .contains("Gradle command available: ./gradlew");
  }

  @Test
  void usesSystemGradleWhenWrapperIsMissing(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle.kts"), "plugins { java }\n");
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection("gradle"));

    assertThat(catalog.gate(project, TESTS).command()).containsExactly("gradle", "test");
    assertThat(catalog.gate(project, TESTS).available()).isTrue();
  }

  @Test
  void offersFullGateCatalogForGradle(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle"), "plugins { id 'java' }\n");
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
    final InitSetupCatalog catalog =
        new InitSetupCatalog(new FakeEnvironmentInspection("gradle").withReleaseTag());

    assertThat(catalog.gates(project, null))
        .extracting(InitGateOption::id)
        .containsExactlyInAnyOrderElementsOf(
            QualityGateFactory.SUPPORTED_GATES.stream()
                // OpenAPI gates are offered only when the project commits a spec.
                .filter(id -> !id.startsWith("openapi-"))
                // Semgrep has no ruleset for JVM projects, so init does not offer it to them.
                .filter(id -> !"semgrep".equals(id))
                // The analysis gate is written by hand in ward.toml; init does not offer it.
                .filter(id -> !SupportedQualityGates.ANALYSIS.equals(id))
                // Node gates are run by check only; init does not offer them for JVM projects.
                .filter(id -> !NODE_GATES.contains(id))
                .filter(id -> !PYTHON_GATES.contains(id))
                // Swift init arrives later; check runs Swift gates only.
                .filter(id -> !SWIFT_GATES.contains(id))
                // The ward-format gate is in every selection, not offered as a choice.
                .filter(id -> !SupportedQualityGates.FORMAT.equals(id))
                // Android gates are offered only for Android projects.
                .filter(id -> !ANDROID_GATES.contains(id))
                .toList());
    assertThat(catalog.gate(project, "coverage").command())
        .containsExactly("gradle", "jacocoTestReport");
    assertThat(catalog.gate(project, "secrets").command()).isEmpty();
  }

  @Test
  void offersBinaryGatesWithPlaceholderTargetAndHints(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());

    assertThat(catalog.gate(project, "zap").command()).contains("-t", "<TARGET_URL>");
    assertThat(catalog.gate(project, "secrets").command()).isEmpty();
    assertThat(catalog.gate(project, "secrets").available()).isTrue();
    assertThat(catalog.gate(project, "enforcer").command()).endsWith("enforcer:enforce");
    assertThat(catalog.gates(project, null))
        .extracting(InitGateOption::id)
        .doesNotContain("semgrep");
  }
}
