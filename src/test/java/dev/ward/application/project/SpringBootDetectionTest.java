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

/** Spring Boot is a framework label on Maven and Gradle projects, not a separate project type. */
class SpringBootDetectionTest {

  private final ProjectDetector detector = new ProjectDetector(new FileSystemProjectInspection());

  @Test
  void detectsSpringBoot(@TempDir final Path dir) {
    withPom(dir, SPRING_BOOT_POM);

    assertThat(detector.detect(dir))
        .get()
        .extracting(Project::framework)
        .isEqualTo(Framework.SPRING_BOOT);
  }

  @Test
  void detectsSpringBootFromDependencyWithoutParent(@TempDir final Path dir) {
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
  void detectsSpringBootFromGradleGroovyPlugin(@TempDir final Path dir) throws Exception {
    Files.writeString(
        dir.resolve("build.gradle"),
        "plugins { id 'java'; id 'org.springframework.boot' version '3.3.0' }\n");

    assertThat(detector.detect(dir))
        .get()
        .extracting(Project::framework)
        .isEqualTo(Framework.SPRING_BOOT);
  }

  @Test
  void detectsSpringBootFromGradleKotlinDependency(@TempDir final Path dir) throws Exception {
    Files.writeString(
        dir.resolve("build.gradle.kts"),
        "dependencies { implementation(\"org.springframework.boot:spring-boot-starter\") }\n");

    assertThat(detector.detect(dir))
        .get()
        .extracting(Project::framework)
        .isEqualTo(Framework.SPRING_BOOT);
  }

  @Test
  void gradleProjectWithoutSpringBootHasNoFramework(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle"), "plugins { id 'java' }\n");

    assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(Framework.NONE);
  }
}
