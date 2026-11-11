package dev.ward.infrastructure.init.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
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

/**
 * The crap gate reads the JaCoCo XML report, so the Ward block makes the coverage task write it.
 */
class GradleCrapReportTest {
  @TempDir Path root;

  @Test
  void groovyBlockEnablesTheXmlReportAndHangsItOnTheVerification() throws IOException {
    final String script = configure("build.gradle", "plugins {\n    id 'java'\n}\n", true);

    assertThat(script)
        .contains(
            "tasks.named('jacocoTestReport') {\n    reports { xml.required = true }\n}",
            "tasks.named('jacocoTestCoverageVerification') { dependsOn 'jacocoTestReport' }");
  }

  @Test
  void kotlinBlockEnablesTheXmlReportAndHangsItOnTheVerification() throws IOException {
    final String script = configure("build.gradle.kts", "plugins {\n    java\n}\n", true);

    assertThat(script)
        .contains(
            "tasks.named<org.gradle.testing.jacoco.tasks.JacocoReport>(\"jacocoTestReport\") {",
            "reports { xml.required.set(true) }",
            "tasks.named(\"jacocoTestCoverageVerification\") { dependsOn(\"jacocoTestReport\") }");
  }

  @Test
  void blockWithoutCrapHasNoXmlReportSetting() throws IOException {
    assertThat(configure("build.gradle", "plugins {\n    id 'java'\n}\n", false))
        .doesNotContain("jacocoTestReport");
  }

  @Test
  void kotlinBlockWithoutCrapHasNoXmlReportSetting() throws IOException {
    assertThat(configure("build.gradle.kts", "plugins {\n    java\n}\n", false))
        .doesNotContain("jacocoTestReport");
  }

  private String configure(final String name, final String content, final boolean crap)
      throws IOException {
    Files.writeString(root.resolve(name), content);
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
    final InitGateOption coverage =
        new InitGateOption(
            "coverage", "coverage", List.of("./gradlew", "coverage"), true, "available");
    final List<InitGateOption> gates =
        crap
            ? List.of(
                coverage, new InitGateOption("crap", "crap", List.of(), true, "reads a report"))
            : List.of(coverage);
    new GradleToolConfigurator().apply(project, gates, null, finding -> false);
    return Files.readString(root.resolve(name));
  }
}
