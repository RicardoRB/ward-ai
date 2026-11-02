package dev.ward.application.project;

import static dev.ward.ProjectFixtures.SPRING_BOOT_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** JVM frameworks are labels on Maven and Gradle projects, not separate project types. */
class JvmFrameworkDetectionTest {

  private final ProjectDetector detector = new ProjectDetector(new FileSystemProjectInspection());

  @TempDir Path dir;

  @ParameterizedTest
  @CsvSource({
    "org.springframework.boot, SPRING_BOOT",
    "io.quarkus, QUARKUS",
    "io.quarkus.platform, QUARKUS",
    "io.micronaut, MICRONAUT",
    "io.helidon.webserver, HELIDON",
    "io.dropwizard, DROPWIZARD",
    "io.javalin, JAVALIN",
    "io.ktor, KTOR"
  })
  void detectsFrameworkFromPomGroupId(final String groupId, final Framework expected) {
    withPom(
        dir,
        "<project><dependencies><dependency><groupId>"
            + groupId
            + "</groupId><artifactId>app</artifactId></dependency></dependencies></project>");

    assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "io.quarkus, QUARKUS",
    "io.micronaut, MICRONAUT",
    "io.helidon, HELIDON",
    "io.dropwizard, DROPWIZARD",
    "io.javalin, JAVALIN",
    "io.ktor, KTOR"
  })
  void detectsFrameworkFromGradleScript(final String groupId, final Framework expected)
      throws Exception {
    Files.writeString(
        dir.resolve("build.gradle.kts"),
        "dependencies { implementation(\"" + groupId + ":app\") }\n");

    assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(expected);
  }

  @Test
  void groupIdThatOnlySharesAPrefixIsNotAFramework() {
    withPom(
        dir,
        "<project><dependencies><dependency><groupId>io.quarkusfork</groupId>"
            + "<artifactId>app</artifactId></dependency></dependencies></project>");

    assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(Framework.NONE);
  }

  @Test
  void detectsSpringBoot() {
    withPom(dir, SPRING_BOOT_POM);

    assertThat(detector.detect(dir))
        .get()
        .extracting(Project::framework)
        .isEqualTo(Framework.SPRING_BOOT);
  }

  @Test
  void detectsSpringBootFromDependencyWithoutParent() {
    withPom(
        dir,
        """
                <project><dependencies><dependency>
                  <groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId>
                </dependency></dependencies></project>
                """);

    assertThat(detector.detect(dir))
        .get()
        .extracting(Project::framework)
        .isEqualTo(Framework.SPRING_BOOT);
  }

  @Test
  void detectsSpringBootFromGradleGroovyPlugin() throws Exception {
    Files.writeString(
        dir.resolve("build.gradle"),
        "plugins { id 'java'; id 'org.springframework.boot' version '3.3.0' }\n");

    assertThat(detector.detect(dir))
        .get()
        .extracting(Project::framework)
        .isEqualTo(Framework.SPRING_BOOT);
  }

  @Test
  void detectsSpringBootFromGradleKotlinDependency() throws Exception {
    Files.writeString(
        dir.resolve("build.gradle.kts"),
        "dependencies { implementation(\"org.springframework.boot:spring-boot-starter\") }\n");

    assertThat(detector.detect(dir))
        .get()
        .extracting(Project::framework)
        .isEqualTo(Framework.SPRING_BOOT);
  }

  @Test
  void gradleProjectWithoutSpringBootHasNoFramework() throws Exception {
    Files.writeString(dir.resolve("build.gradle"), "plugins { id 'java' }\n");

    assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(Framework.NONE);
  }
}
