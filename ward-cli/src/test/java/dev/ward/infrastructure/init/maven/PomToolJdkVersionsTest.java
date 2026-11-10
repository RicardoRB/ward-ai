package dev.ward.infrastructure.init.maven;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.CompatibilityProjectSnapshot;
import dev.ward.domain.init.InitGateOption;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Maven plugin and engine versions init writes for the project's runtime JDK. */
class PomToolJdkVersionsTest {
  private static final String POM = "pom.xml";
  private static final String EMPTY_POM = "<project><modelVersion>4.0.0</modelVersion></project>";
  private static final int JAVA_8 = 8;
  private static final int JAVA_25 = 25;
  private static final int JAVA_27 = 27;
  private static final List<String> STRICT_GATES = List.of("coverage", "enforcer", "mutation");

  @Test
  void jdk8StrictProjectGetsTheJdk8Rows(@TempDir final Path root) throws IOException {
    final String pom = strictPom(root, JAVA_8);

    assertThat(pom)
        .contains(
            "<artifactId>jacoco-maven-plugin</artifactId>",
            "<version>0.8.13</version>",
            "<version>1.17.4</version>")
        .doesNotContain("ENGINE_VERSION");
  }

  @Test
  void jdk25StrictProjectKeepsTheCurrentPins(@TempDir final Path root) throws IOException {
    final String pom = strictPom(root, JAVA_25);

    assertThat(pom)
        .contains(
            "<artifactId>jacoco-maven-plugin</artifactId>",
            "<version>0.8.13</version>",
            "<version>1.30.1</version>");
  }

  @Test
  void jdkAbove25IsConfiguredAsJdk25WithAWarning(
      @TempDir final Path jdk25, @TempDir final Path jdk27) throws IOException {
    final PomToolConfigurator configurator =
        new PomToolConfigurator(ignored -> snapshot(JAVA_27, Map.of()));
    writePom(jdk27, EMPTY_POM);

    final BuildFileChange change =
        configurator.apply(
            project(jdk27),
            STRICT_GATES.stream().map(PomToolJdkVersionsTest::gate).toList(),
            QualityPreset.STRICT,
            finding -> true);

    assertThat(Files.readString(jdk27.resolve(POM))).isEqualTo(strictPom(jdk25, JAVA_25));
    assertThat(change.warnings())
        .containsExactly(
            "Ward has not been tested on JDK 27 and does not support it; it is configured as"
                + " JDK 25.");
  }

  private static String strictPom(final Path root, final int jdk) throws IOException {
    writePom(root, "<project><modelVersion>4.0.0</modelVersion></project>");
    new PomToolConfigurator(ignored -> snapshot(jdk, Map.of()))
        .apply(
            project(root),
            STRICT_GATES.stream().map(PomToolJdkVersionsTest::gate).toList(),
            QualityPreset.STRICT,
            finding -> true);
    return Files.readString(root.resolve(POM));
  }

  private static CompatibilityProjectSnapshot snapshot(
      final int javaVersion, final Map<String, String> plugins) {
    return new CompatibilityProjectSnapshot(
        new Project(Path.of("."), Language.JAVA, BuildTool.MAVEN, Framework.NONE),
        new ToolchainSnapshot(
            Optional.of(javaVersion), Optional.empty(), Optional.of("3.9.0"), Framework.NONE),
        plugins);
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }

  private static void writePom(final Path root, final String content) throws IOException {
    Files.writeString(root.resolve(POM), content);
  }
}
