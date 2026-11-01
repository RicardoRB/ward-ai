package dev.ward.application.init;

import static dev.ward.application.init.CiWorkflowTemplateTest.MAVEN;
import static dev.ward.application.init.CiWorkflowTemplateTest.gates;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.PackageManifest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The generated CI workflow sets up the JDK that runs the project's build. */
class CiWorkflowJdkTest {
  private static final int JAVA_11 = 11;
  private static final String GITHUB = "github";
  private static final String TESTS = "tests";
  private static final String DEV = "dev";

  @Test
  void jdk11ProjectSetsUpJdk11() {
    final String body =
        CiWorkflowTemplate.render(
                GITHUB,
                MAVEN,
                gates(TESTS),
                DEV,
                new CiWorkflowTemplate.ProjectFacts(
                    PackageManifest.EMPTY, Optional.of(JAVA_11), false))
            .body();

    assertThat(body).contains("distribution: temurin\n          java-version: '11'\n");
  }

  @Test
  void unknownJdkSetsUpJdk25() {
    assertThat(CiWorkflowTemplate.render(GITHUB, MAVEN, gates(TESTS), DEV).body())
        .contains("java-version: '25'\n");
  }
}
