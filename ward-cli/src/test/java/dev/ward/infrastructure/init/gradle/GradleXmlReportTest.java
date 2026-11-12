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

/** Ward reads the JaCoCo XML report, so the block enables it and sets no coverage rule. */
class GradleXmlReportTest {
  @TempDir Path root;

  @Test
  void groovyBlockEnablesTheXmlReportAndSetsNoRule() throws IOException {
    final String script = configure("build.gradle", "plugins {\n    id 'java'\n}\n");

    assertThat(script)
        .contains(
            "tasks.named('jacocoTestReport') {\n    reports { xml.required = true }\n}",
            "tasks.named('jacocoTestReport') { dependsOn 'wardCoverageData' }")
        .doesNotContain("violationRules", "jacocoTestCoverageVerification");
  }

  @Test
  void kotlinBlockEnablesTheXmlReportAndSetsNoRule() throws IOException {
    final String script = configure("build.gradle.kts", "plugins {\n    java\n}\n");

    assertThat(script)
        .contains(
            "tasks.named<org.gradle.testing.jacoco.tasks.JacocoReport>(\"jacocoTestReport\") {",
            "reports { xml.required.set(true) }",
            "tasks.named(\"jacocoTestReport\") { dependsOn(\"wardCoverageData\") }")
        .doesNotContain("violationRules", "jacocoTestCoverageVerification");
  }

  @Test
  void aBlockFromBeforeGetsTheXmlReportAddedAndNothingElse() throws IOException {
    final String earlier =
        "plugins {\n    id 'java'\n}\n\n// Ward-managed quality gate configuration\n"
            + "jacocoTestCoverageVerification {\n    violationRules { }\n}\n"
            + "tasks.register('wardCoverageData') { }\n";

    final String script = configure("build.gradle", earlier);

    assertThat(script).startsWith(earlier.substring(earlier.indexOf("plugins")).substring(0, 20));
    assertThat(script).contains("xml.required = true");
    assertThat(script.indexOf("tasks.register('wardCoverageData')"))
        .isEqualTo(script.lastIndexOf("tasks.register('wardCoverageData')"));
  }

  private String configure(final String name, final String content) throws IOException {
    Files.writeString(root.resolve(name), content);
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
    final InitGateOption coverage =
        new InitGateOption(
            "coverage", "coverage", List.of("./gradlew", "jacocoTestReport"), true, "available");
    new GradleToolConfigurator().apply(project, List.of(coverage), null, finding -> false);
    return Files.readString(root.resolve(name));
  }
}
