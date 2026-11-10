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

class GradleSourceLayoutsTest {
  private static final String FIXTURES = "src/test/resources/fixtures/";
  private static final String JAVA_MATRIX = FIXTURES + "java-matrix/";
  private static final String MAIN_JAVA = "src/main/java";
  private static final String TEST_JAVA = "src/test/java";
  private static final String MAIN_KOTLIN = "src/main/kotlin";
  private static final String TEST_KOTLIN = "src/test/kotlin";
  private static final String ROOT_MODULE = "";
  private static final String DOMAIN = "domain";

  @TempDir Path root;

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
            new JavaSourceRoot("generated/main", ROOT_MODULE, false),
            new JavaSourceRoot(TEST_JAVA, ROOT_MODULE, true),
            new JavaSourceRoot(MAIN_KOTLIN, ROOT_MODULE, false),
            new JavaSourceRoot("generated/kotlin", ROOT_MODULE, false),
            new JavaSourceRoot(TEST_KOTLIN, ROOT_MODULE, true));
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
            new JavaSourceRoot(MAIN_JAVA, ROOT_MODULE, false),
            new JavaSourceRoot("generated/test", ROOT_MODULE, true),
            new JavaSourceRoot(MAIN_KOTLIN, ROOT_MODULE, false),
            new JavaSourceRoot(TEST_KOTLIN, ROOT_MODULE, true));
  }

  @Test
  void multiModuleGradleProjectGivesEachModuleItsOwnRoots() throws IOException {
    final Path api = Files.createDirectories(root.resolve("api"));
    final Path domain = Files.createDirectories(root.resolve(DOMAIN));
    write(api.resolve("build.gradle"), "plugins { id 'java' }");
    write(domain.resolve("build.gradle.kts"), "plugins { java }");

    assertThat(JvmSourceLayouts.of(root, List.of(api, domain)))
        .containsExactly(
            new JavaSourceRoot("api/src/main/java", "api", false),
            new JavaSourceRoot("api/src/test/java", "api", true),
            new JavaSourceRoot("api/src/main/kotlin", "api", false),
            new JavaSourceRoot("api/src/test/kotlin", "api", true),
            new JavaSourceRoot("domain/src/main/java", DOMAIN, false),
            new JavaSourceRoot("domain/src/test/java", DOMAIN, true),
            new JavaSourceRoot("domain/src/main/kotlin", DOMAIN, false),
            new JavaSourceRoot("domain/src/test/kotlin", DOMAIN, true));
  }

  @Test
  void gradleKotlinModuleFixtureGivesKotlinDefaultRoots() {
    final Path project = Path.of(FIXTURES, "gradle-kotlin-modules");

    assertThat(JvmSourceLayouts.of(project, List.of(project.resolve(DOMAIN))))
        .containsExactly(
            new JavaSourceRoot("domain/src/main/java", DOMAIN, false),
            new JavaSourceRoot("domain/src/test/java", DOMAIN, true),
            new JavaSourceRoot("domain/src/main/kotlin", DOMAIN, false),
            new JavaSourceRoot("domain/src/test/kotlin", DOMAIN, true));
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
            new JavaSourceRoot(MAIN_JAVA, ROOT_MODULE, false),
            new JavaSourceRoot(TEST_JAVA, ROOT_MODULE, true),
            new JavaSourceRoot(MAIN_KOTLIN, ROOT_MODULE, false),
            new JavaSourceRoot(TEST_KOTLIN, ROOT_MODULE, true));
  }

  private static List<JavaSourceRoot> roots(final Path project) {
    return JvmSourceLayouts.of(project, List.of(project));
  }

  private static void write(final Path file, final String text) throws IOException {
    Files.writeString(file, text);
  }
}
