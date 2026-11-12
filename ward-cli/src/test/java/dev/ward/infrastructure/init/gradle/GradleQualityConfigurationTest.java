package dev.ward.infrastructure.init.gradle;

import static org.assertj.core.api.Assertions.assertThat;

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

/** Gradle accepts the generated build script, and the plugins use what init generates. */
class GradleQualityConfigurationTest {
  private static final List<String> STRICT_GATES =
      List.of("coverage", "archunit", "enforcer", "mutation", "dependency-check");

  @TempDir Path root;

  @Test
  void addsPluginsInsideTheExistingPluginsBlock() throws IOException {
    final String script =
        configure("build.gradle", "plugins {\n    id 'java'\n}\n\nrepositories {}\n", null);

    assertThat(script)
        .startsWith("plugins {\n    " + GradlePluginsBlock.MARKER + "\n    id 'jacoco'\n")
        .contains("    id 'info.solidsoft.pitest' version '1.19.0'\n    id 'java'\n}\n");
    assertThat(script.indexOf("plugins {")).isEqualTo(script.lastIndexOf("plugins {"));
  }

  @Test
  void createsAPluginsBlockAfterBuildscriptWhenThereIsNone() throws IOException {
    final String script =
        configure(
            "build.gradle",
            "buildscript {\n    repositories { mavenCentral() }\n}\napply plugin: 'java'\n",
            null);

    assertThat(script)
        .startsWith("buildscript {\n    repositories { mavenCentral() }\n}\n\nplugins {\n")
        .contains("}\napply plugin: 'java'\n");
  }

  @Test
  void prependsAPluginsBlockToAScriptWithoutOne() throws IOException {
    assertThat(configure("build.gradle", "apply plugin: 'java'\n", null))
        .startsWith("plugins {\n    " + GradlePluginsBlock.MARKER);
  }

  @Test
  void wiresPresetRuleFilesThresholdsAndMainSourcesForGroovy() throws IOException {
    final String script =
        configure("build.gradle", "plugins {\n    id 'java'\n}\n", QualityPreset.STRICT);

    assertThat(script)
        .contains(
            "jacoco {\n    toolVersion = '0.8.13'\n}\ntasks.register('wardCoverageData')",
            "reports { xml.required = true }",
            "pitest {\n    pitestVersion = '1.30.1'\n    junit5PluginVersion = '1.2.3'\n"
                + "    outputFormats = ['XML', 'HTML']\n}\n"
                + "tasks.named('pitest') { outputs.upToDateWhen { false } }")
        .doesNotContain(
            "mutationThreshold",
            "historyInputFile",
            "historyOutputFile",
            "violationRules",
            "minimum = 0.");
  }

  @Test
  void usesKotlinSyntaxAndTaskAccessorsForKotlinScripts() throws IOException {
    final String script =
        configure("build.gradle.kts", "plugins {\n    java\n}\n", QualityPreset.STRICT);

    assertThat(script)
        .contains(
            "reports { xml.required.set(true) }",
            "pitestVersion = \"1.30.1\"",
            "junit5PluginVersion = \"1.2.3\"",
            "outputFormats = listOf(\"XML\", \"HTML\")")
        .doesNotContain("historyInputFile", "historyOutputFile");
  }

  @Test
  void withoutAPresetKeepsGradleRuleDefaultsAndStillWritesTheCoverageReport() throws IOException {
    final String script = configure("build.gradle", "plugins {\n    id 'java'\n}\n", null);

    assertThat(script)
        .doesNotContain("mutationThreshold", "config/checkstyle.xml", "violationRules")
        .contains("reports { xml.required = true }");
  }

  @Test
  void runningAgainChangesNothing() throws IOException {
    final String first =
        configure("build.gradle", "plugins {\n    id 'java'\n}\n", QualityPreset.STRICT);

    final BuildFileChange second =
        new GradleToolConfigurator()
            .apply(project(), gates(), QualityPreset.STRICT, finding -> false);

    assertThat(second.changed()).isFalse();
    assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(first);
  }

  private String configure(final String file, final String script, final QualityPreset preset)
      throws IOException {
    Files.writeString(root.resolve(file), script);
    new GradleToolConfigurator().apply(project(), gates(), preset, finding -> false);
    return Files.readString(root.resolve(file));
  }

  private Project project() {
    return new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
  }

  private static List<InitGateOption> gates() {
    return STRICT_GATES.stream()
        .map(id -> new InitGateOption(id, id, List.of("./gradlew", id), true, "available"))
        .toList();
  }
}
