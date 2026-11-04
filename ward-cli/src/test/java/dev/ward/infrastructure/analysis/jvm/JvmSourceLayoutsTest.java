package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.jvm.JavaSourceRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JvmSourceLayoutsTest {
  private static final String FIXTURES = "src/test/resources/fixtures/";
  private static final String JAVA_MATRIX = FIXTURES + "java-matrix/";

  @TempDir Path root;

  @Test
  void mavenDefaultRootsOfTheRootModule() throws IOException {
    write(root.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");

    assertThat(roots(root))
        .containsExactly(
            new JavaSourceRoot("src/main/java", "", false),
            new JavaSourceRoot("src/test/java", "", true),
            new JavaSourceRoot("src/main/kotlin", "", false),
            new JavaSourceRoot("src/test/kotlin", "", true));
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
            new JavaSourceRoot("src/main/kotlin", "", false),
            new JavaSourceRoot("src/test/kotlin", "", true));
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
            new JavaSourceRoot("src/main/java", "", false),
            new JavaSourceRoot("src/test/java", "", true),
            new JavaSourceRoot("src/main/kotlin", "", false),
            new JavaSourceRoot("src/test/kotlin", "", true));
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
            new JavaSourceRoot("src/main/java", "", false),
            new JavaSourceRoot("src/test/java", "", true),
            new JavaSourceRoot("src/main/kotlin", "", false),
            new JavaSourceRoot("generated/kotlin", "", false),
            new JavaSourceRoot("src/test/kotlin", "", true),
            new JavaSourceRoot("src/test/extra", "", true));
  }

  @Test
  void gradleKotlinLiteralSourceSetDirectoryIsRead() throws IOException {
    write(
        root.resolve("build.gradle.kts"),
        """
        sourceSets {
            main {
                java {
                    srcDir("generated/main")
                }
                kotlin {
                    srcDir("generated/kotlin")
                }
            }
        }
        """);

    assertThat(roots(root))
        .containsExactly(
            new JavaSourceRoot("generated/main", "", false),
            new JavaSourceRoot("src/test/java", "", true),
            new JavaSourceRoot("src/main/kotlin", "", false),
            new JavaSourceRoot("generated/kotlin", "", false),
            new JavaSourceRoot("src/test/kotlin", "", true));
  }

  @Test
  void gradleGroovyLiteralTestDirectoryIsRead() throws IOException {
    write(
        root.resolve("build.gradle"),
        """
        sourceSets {
            test {
                java {
                    srcDirs = ['generated/test']
                }
            }
        }
        """);

    assertThat(roots(root))
        .containsExactly(
            new JavaSourceRoot("src/main/java", "", false),
            new JavaSourceRoot("generated/test", "", true),
            new JavaSourceRoot("src/main/kotlin", "", false),
            new JavaSourceRoot("src/test/kotlin", "", true));
  }

  @Test
  void multiModuleGradleProjectGivesEachModuleItsOwnRoots() throws IOException {
    final Path api = Files.createDirectories(root.resolve("api"));
    final Path domain = Files.createDirectories(root.resolve("domain"));
    write(api.resolve("build.gradle"), "plugins { id 'java' }");
    write(domain.resolve("build.gradle.kts"), "plugins { java }");

    assertThat(JvmSourceLayouts.of(root, List.of(api, domain)))
        .containsExactly(
            new JavaSourceRoot("api/src/main/java", "api", false),
            new JavaSourceRoot("api/src/test/java", "api", true),
            new JavaSourceRoot("api/src/main/kotlin", "api", false),
            new JavaSourceRoot("api/src/test/kotlin", "api", true),
            new JavaSourceRoot("domain/src/main/java", "domain", false),
            new JavaSourceRoot("domain/src/test/java", "domain", true),
            new JavaSourceRoot("domain/src/main/kotlin", "domain", false),
            new JavaSourceRoot("domain/src/test/kotlin", "domain", true));
  }

  @Test
  void gradleKotlinModuleFixtureGivesKotlinDefaultRoots() {
    final Path project = Path.of(FIXTURES, "gradle-kotlin-modules");

    assertThat(JvmSourceLayouts.of(project, List.of(project.resolve("domain"))))
        .containsExactly(
            new JavaSourceRoot("domain/src/main/java", "domain", false),
            new JavaSourceRoot("domain/src/test/java", "domain", true),
            new JavaSourceRoot("domain/src/main/kotlin", "domain", false),
            new JavaSourceRoot("domain/src/test/kotlin", "domain", true));
  }

  /** The java-matrix build files, each copied under the name its build tool reads. */
  @ParameterizedTest
  @CsvSource({
    "maven/pom.xml, pom.xml",
    "gradle-kotlin/build.gradle.kts, build.gradle.kts",
    "gradle-groovy/build.gradle, build.gradle"
  })
  void theJavaMatrixBuildFilesUseTheDefaultRoots(final String fixture, final String name)
      throws IOException {
    Files.copy(Path.of(JAVA_MATRIX, fixture), root.resolve(name));

    assertThat(roots(root))
        .containsExactly(
            new JavaSourceRoot("src/main/java", "", false),
            new JavaSourceRoot("src/test/java", "", true),
            new JavaSourceRoot("src/main/kotlin", "", false),
            new JavaSourceRoot("src/test/kotlin", "", true));
  }

  private static List<JavaSourceRoot> roots(final Path project) {
    return JvmSourceLayouts.of(project, List.of(project));
  }

  private static void write(final Path file, final String text) throws IOException {
    Files.writeString(file, text);
  }
}
