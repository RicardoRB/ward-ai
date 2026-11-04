package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.jvm.JavaSourceRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JvmSourceLayoutsTest {
  private static final String MAIN_JAVA = "src/main/java";
  private static final String TEST_JAVA = "src/test/java";
  private static final String MAIN_KOTLIN = "src/main/kotlin";
  private static final String TEST_KOTLIN = "src/test/kotlin";

  @TempDir Path root;

  @Test
  void mavenDefaultRootsOfTheRootModule() throws IOException {
    write(root.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");

    assertThat(roots(root))
        .containsExactly(
            new JavaSourceRoot(MAIN_JAVA, "", false),
            new JavaSourceRoot(TEST_JAVA, "", true),
            new JavaSourceRoot(MAIN_KOTLIN, "", false),
            new JavaSourceRoot(TEST_KOTLIN, "", true));
  }

  @Test
  void mavenLiteralCustomRootsReplaceTheDefaults() throws IOException {
    write(
        root.resolve("pom.xml"),
        """
        <project>
          <build>
            <sourceDirectory>src/app</sourceDirectory>
            <testSourceDirectory>tests/java</testSourceDirectory>
          </build>
        </project>
        """);

    assertThat(roots(root))
        .containsExactly(
            new JavaSourceRoot("src/app", "", false),
            new JavaSourceRoot("tests/java", "", true),
            new JavaSourceRoot(MAIN_KOTLIN, "", false),
            new JavaSourceRoot(TEST_KOTLIN, "", true));
  }

  @Test
  void mavenRootsBuiltFromPropertiesFallBackToTheDefaults() throws IOException {
    write(
        root.resolve("pom.xml"),
        """
        <project>
          <build>
            <sourceDirectory>${project.basedir}/src/app</sourceDirectory>
          </build>
        </project>
        """);

    assertThat(roots(root))
        .containsExactly(
            new JavaSourceRoot(MAIN_JAVA, "", false),
            new JavaSourceRoot(TEST_JAVA, "", true),
            new JavaSourceRoot(MAIN_KOTLIN, "", false),
            new JavaSourceRoot(TEST_KOTLIN, "", true));
  }

  @Test
  void mavenLiteralKotlinSourceDirsAddToTheDefaults() throws IOException {
    write(
        root.resolve("pom.xml"),
        """
        <project>
          <build>
            <plugins>
              <plugin>
                <artifactId>kotlin-maven-plugin</artifactId>
                <executions>
                  <execution>
                    <configuration>
                      <sourceDirs>
                        <sourceDir>${project.basedir}/src/main/kotlin</sourceDir>
                        <sourceDir>generated/kotlin</sourceDir>
                        <sourceDir>src/test/extra</sourceDir>
                      </sourceDirs>
                    </configuration>
                  </execution>
                </executions>
              </plugin>
            </plugins>
          </build>
        </project>
        """);

    assertThat(roots(root))
        .containsExactly(
            new JavaSourceRoot(MAIN_JAVA, "", false),
            new JavaSourceRoot(TEST_JAVA, "", true),
            new JavaSourceRoot(MAIN_KOTLIN, "", false),
            new JavaSourceRoot("generated/kotlin", "", false),
            new JavaSourceRoot(TEST_KOTLIN, "", true),
            new JavaSourceRoot("src/test/extra", "", true));
  }

  private static List<JavaSourceRoot> roots(final Path project) {
    return JvmSourceLayouts.of(project, List.of(project));
  }

  private static void write(final Path file, final String text) throws IOException {
    Files.writeString(file, text);
  }
}
