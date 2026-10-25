package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.CompatibilityProjectSnapshot;
import dev.ward.domain.init.GradleToolCatalog;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.ManagedPluginCandidate;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.ToolchainSnapshot;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GradleToolCompatibilityTest {
  private static final int JAVA_7 = 7;
  private static final int JAVA_11 = 11;
  private static final List<String> STRICT_GATES =
      List.of(
          "format",
          "checkstyle",
          "pmd",
          "spotbugs",
          "coverage",
          "archunit",
          "enforcer",
          "mutation");

  private static final List<String> STRICT_TOOLS =
      List.of(
          GradleToolCatalog.SPOTLESS,
          GradleToolCatalog.FORMATTER,
          GradleToolCatalog.CHECKSTYLE_ENGINE,
          GradleToolCatalog.PMD_ENGINE,
          GradleToolCatalog.SPOTBUGS,
          GradleToolCatalog.SPOTBUGS_ENGINE,
          GradleToolCatalog.JACOCO_ENGINE,
          GradleToolCatalog.PITEST,
          GradleToolCatalog.PITEST_ENGINE,
          GradleToolCatalog.ARCHUNIT);

  @TempDir Path root;

  @ParameterizedTest
  @ValueSource(strings = {"build.gradle", "build.gradle.kts"})
  void gradleProjectOnJdk11GetsOnlyVersionsThatRunOnJdk11(final String file) throws IOException {
    final String script = configure(file, JAVA_11).script();

    final List<String> wrong =
        GradleToolCatalog.entries().values().stream()
            .flatMap(List::stream)
            .filter(row -> STRICT_TOOLS.contains(row.tool()))
            .filter(row -> row.runsOn(JAVA_11) != script.contains(written(file, row)))
            .map(row -> row.tool() + " " + row.version())
            .toList();

    assertThat(wrong).as("versions written or skipped against their JDK 11 range").isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"build.gradle", "build.gradle.kts"})
  void gatesWithNoVersionForTheJdkAreOmittedWithAWarning(final String file) throws IOException {
    final Configured configured = configure(file, JAVA_7);

    assertThat(configured.script()).doesNotContain("com.diffplug.spotless", "pitest {");
    assertThat(configured.change().warnings())
        .contains(
            "format omitted: com.diffplug.spotless needs JDK 8+; this project builds on JDK 7")
        .anyMatch(warning -> warning.startsWith("Ward supports Java 8 to 25"));
  }

  private Configured configure(final String file, final int jdk) throws IOException {
    final Path script = root.resolve(file);
    Files.writeString(script, "plugins {\n    java\n}\n");
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
    final BuildFileChange change =
        new GradleToolConfigurator(ignored -> snapshot(project, jdk))
            .apply(
                project,
                STRICT_GATES.stream()
                    .map(id -> new InitGateOption(id, id, List.of("gradle", id), true, "ok"))
                    .toList(),
                QualityPreset.STRICT,
                finding -> true);
    return new Configured(Files.readString(script), change);
  }

  /** How the build file writes {@code row}: ArchUnit as a coordinate, the rest quoted. */
  private static String written(final String file, final ManagedPluginCandidate row) {
    return GradleToolCatalog.ARCHUNIT.equals(row.tool())
        ? "archunit-junit5:" + row.version()
        : quoted(file, row.version());
  }

  /** A version as the DSL writes it, so {@code 1.7} does not match inside {@code 1.17.4}. */
  private static String quoted(final String file, final String version) {
    final String quote = file.endsWith(".kts") ? "\"" : "'";
    return quote + version + quote;
  }

  private static CompatibilityProjectSnapshot snapshot(final Project project, final int jdk) {
    return new CompatibilityProjectSnapshot(
        project,
        new ToolchainSnapshot(
            Optional.of(jdk), Optional.empty(), Optional.of("8.14.6"), Framework.NONE),
        Map.of());
  }

  private record Configured(String script, BuildFileChange change) {}
}
