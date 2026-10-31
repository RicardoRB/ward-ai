package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Module placement: each Kotlin module gets its gates in its own script, the root the rest. */
class GradleToolConfiguratorModulesTest {
  private static final String API = "api/build.gradle.kts";
  private static final String DOMAIN = "domain/build.gradle";
  private static final String COVERAGE = "coverage";

  @TempDir Path root;

  @Test
  void placesCoverageAndSpotBugsInEachKotlinModule() throws IOException {
    final Project project = ModuleFixtures.copy(root);

    new GradleToolConfigurator()
        .apply(project, ModuleFixtures.gates(COVERAGE, "spotbugs"), null, finding -> false);

    assertThat(ModuleFixtures.read(root, API))
        .contains(
            "id(\"jacoco\")", "jacocoTestCoverageVerification", "id(\"com.github.spotbugs\")");
    assertThat(ModuleFixtures.read(root, DOMAIN))
        .contains("id 'jacoco'", "id 'com.github.spotbugs'");
    assertThat(ModuleFixtures.read(root, "build.gradle.kts"))
        .doesNotContain("jacocoTestCoverageVerification");
  }

  @Test
  void versionedModulePluginIsDeclaredOnceInTheRootWithApplyFalse() throws IOException {
    final Project project = ModuleFixtures.copy(root);

    new GradleToolConfigurator()
        .apply(project, ModuleFixtures.gates("spotbugs"), null, finding -> false);

    assertThat(ModuleFixtures.read(root, "build.gradle.kts"))
        .containsPattern("id\\(\"com\\.github\\.spotbugs\"\\) version \"[^\"]+\" apply false");
    assertThat(ModuleFixtures.read(root, API))
        .contains("id(\"com.github.spotbugs\")")
        .doesNotContain("com.github.spotbugs\") version");
    assertThat(ModuleFixtures.read(root, DOMAIN))
        .contains("id 'com.github.spotbugs'")
        .doesNotContain("com.github.spotbugs' version");
  }

  @Test
  void groovyModuleKeepsGroovyDsl() throws IOException {
    final Project project = ModuleFixtures.copy(root);

    new GradleToolConfigurator()
        .apply(project, ModuleFixtures.gates(COVERAGE), null, finding -> false);

    assertThat(ModuleFixtures.read(root, DOMAIN))
        .contains("jacocoTestCoverageVerification {")
        .doesNotContain("tasks.jacocoTestCoverageVerification");
  }

  @Test
  void dependencyCheckStaysInTheRootOnly() throws IOException {
    final Project project = ModuleFixtures.copy(root);

    new GradleToolConfigurator()
        .apply(project, ModuleFixtures.gates("dependency-check", COVERAGE), null, finding -> false);

    assertThat(ModuleFixtures.read(root, "build.gradle.kts")).contains("org.owasp.dependencycheck");
    assertThat(ModuleFixtures.read(root, API)).doesNotContain("org.owasp.dependencycheck");
    assertThat(ModuleFixtures.read(root, DOMAIN)).doesNotContain("org.owasp.dependencycheck");
  }

  @Test
  void checkstyleAndPmdAndEnforcerGoIntoEachKotlinModuleWithRootRuleFiles() throws IOException {
    final Project project = ModuleFixtures.copy(root);

    new GradleToolConfigurator()
        .apply(
            project,
            ModuleFixtures.gates("checkstyle", "pmd", "enforcer"),
            QualityPreset.STANDARD,
            finding -> false);

    assertThat(ModuleFixtures.read(root, API))
        .contains(
            "configFile = rootProject.file(\"config/checkstyle.xml\")",
            "ruleSetFiles = rootProject.files(\"config/pmd-ruleset.xml\")",
            "tasks.named(\"check\") { dependsOn(\"checkstyleMain\") }");
    assertThat(ModuleFixtures.read(root, DOMAIN))
        .contains("rootProject.file('config/checkstyle.xml')", "rootProject.files(")
        .doesNotContain("configFile = file(");
    assertThat(ModuleFixtures.read(root, "build.gradle.kts"))
        .doesNotContain("checkstyle {", "pmd {", "sourceSets");
  }

  @Test
  void singleModuleKotlinRootKeepsEveryBlockInTheRoot() throws IOException {
    Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"solo\"\n");
    Files.writeString(
        root.resolve("build.gradle.kts"), "plugins {\n    kotlin(\"jvm\") version \"2.1.0\"\n}\n");
    final Project project = new Project(root, Language.KOTLIN, BuildTool.GRADLE, Framework.NONE);

    final BuildFileChange change =
        new GradleToolConfigurator()
            .apply(project, ModuleFixtures.gates(COVERAGE), null, finding -> false);

    assertThat(ModuleFixtures.read(root, "build.gradle.kts"))
        .contains("jacocoTestCoverageVerification");
    assertThat(change.moduleScripts()).isEmpty();
  }

  @Test
  void rerunChangesNoScript() throws IOException {
    final Project project = ModuleFixtures.copy(root);
    final GradleToolConfigurator configurator = new GradleToolConfigurator();
    final List<InitGateOption> gates = ModuleFixtures.gates(COVERAGE, "spotbugs");
    configurator.apply(project, gates, null, finding -> false);
    final String api = ModuleFixtures.read(root, API);

    final BuildFileChange second = configurator.apply(project, gates, null, finding -> false);

    assertThat(second.changed()).isFalse();
    assertThat(second.moduleScripts()).isEmpty();
    assertThat(ModuleFixtures.read(root, API)).isEqualTo(api);
  }

  @Test
  void rollbackRestoresEveryModuleScript() throws IOException {
    final Project project = ModuleFixtures.copy(root);
    final GradleToolConfigurator configurator = new GradleToolConfigurator();
    final String api = ModuleFixtures.read(root, API);
    final String domain = ModuleFixtures.read(root, DOMAIN);

    final BuildFileChange change =
        configurator.apply(project, ModuleFixtures.gates(COVERAGE), null, finding -> false);
    configurator.rollback(change);

    assertThat(ModuleFixtures.read(root, API)).isEqualTo(api);
    assertThat(ModuleFixtures.read(root, DOMAIN)).isEqualTo(domain);
  }

  @Test
  void failedModuleWriteLeavesEveryScriptAsBefore() throws IOException {
    final Project project = ModuleFixtures.copy(root);
    final Path domain = root.resolve(DOMAIN);
    final String api = ModuleFixtures.read(root, API);
    final String domainOriginal = ModuleFixtures.read(root, DOMAIN);
    domain.toFile().setWritable(false);
    assumeFalse(Files.isWritable(domain), "read-only files are writable for this user");

    assertThatThrownBy(
            () ->
                new GradleToolConfigurator()
                    .apply(project, ModuleFixtures.gates(COVERAGE), null, finding -> false))
        .isInstanceOf(WardException.class);

    assertThat(ModuleFixtures.read(root, API)).isEqualTo(api);
    assertThat(ModuleFixtures.read(root, DOMAIN)).isEqualTo(domainOriginal);
    domain.toFile().setWritable(true);
  }

  @Test
  void planListsEachModuleScriptItWouldChange() throws IOException {
    final Project project = ModuleFixtures.copy(root);

    final List<PlannedFileChange> plans =
        new GradleToolConfigurator()
            .planModules(project, ModuleFixtures.gates(COVERAGE), null, false);

    assertThat(plans)
        .extracting(plan -> root.relativize(plan.file()).toString())
        .containsExactlyInAnyOrder(API, DOMAIN);
    assertThat(plans.getFirst().details()).anyMatch(detail -> detail.contains("jacoco"));
    assertThat(ModuleFixtures.read(root, API)).doesNotContain("jacoco");
  }
}
