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

/** A module whose tests left no JaCoCo data fails the coverage gate instead of passing. */
class GradleCoverageDataTest {
  @TempDir Path root;

  @Test
  void groovyCoverageFailsWhenNoTestWroteExecutionData() throws IOException {
    final String script = configure("build.gradle", "plugins {\n    id 'java'\n}\n");

    assertThat(script)
        .contains(
            "tasks.register('wardCoverageData') {\n    dependsOn 'test'",
            "throw new org.gradle.api.GradleException(",
            "tasks.named('jacocoTestCoverageVerification') { dependsOn 'wardCoverageData' }");
  }

  @Test
  void kotlinCoverageFailsWhenNoTestWroteExecutionData() throws IOException {
    final String script = configure("build.gradle.kts", "plugins {\n    java\n}\n");

    assertThat(script)
        .contains(
            "tasks.register(\"wardCoverageData\") {\n    dependsOn(\"test\")",
            "check(exec != null && exec.exists())",
            "tasks.named(\"jacocoTestCoverageVerification\") { dependsOn(\"wardCoverageData\") }");
  }

  private String configure(final String name, final String content) throws IOException {
    Files.writeString(root.resolve(name), content);
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
    new GradleToolConfigurator()
        .apply(
            project,
            List.of(
                new InitGateOption(
                    "coverage", "coverage", List.of("./gradlew", "coverage"), true, "available")),
            null,
            finding -> false);
    return Files.readString(root.resolve(name));
  }
}
