package dev.ward.infrastructure.init;

import static dev.ward.infrastructure.init.PomToolConfiguratorCompatibilityTest.gate;
import static dev.ward.infrastructure.init.PomToolConfiguratorCompatibilityTest.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.QualityPreset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Maven plugin and engine versions init writes for the project's runtime JDK. */
class PomToolJdkVersionsTest {
  private static final String POM = "pom.xml";
  private static final int JAVA_8 = 8;
  private static final int JAVA_25 = 25;
  private static final int JAVA_27 = 27;
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

  @Test
  void jdk8StrictProjectGetsTheJdk8Rows(@TempDir final Path root) throws IOException {
    final String pom = strictPom(root, JAVA_8);

    assertThat(pom)
        .contains(
            "<artifactId>checkstyle</artifactId><version>9.3</version>",
            "<artifactId>spotbugs-maven-plugin</artifactId><version>4" + ".8.6.6</version>",
            "<version>1.17.4</version>",
            "<version>2.30.0</version>",
            "<googleJavaFormat><version>1.7</version></googleJavaFormat>",
            "<version>1.5.1</version>")
        .doesNotContain("ENGINE_VERSION");
  }

  @Test
  void jdk25StrictProjectKeepsTheCurrentPinsWithANewerArchUnit(@TempDir final Path root)
      throws IOException {
    final String pom = strictPom(root, JAVA_25);

    assertThat(pom)
        .contains(
            "<version>3.6.0</version>",
            "<artifactId>checkstyle</artifactId><version>10.20.2</version>",
            "<artifactId>spotbugs-maven-plugin</artifactId><version>4" + ".9.7.0</version>",
            "<version>1.30.1</version>",
            "<version>3.10.3</version>",
            "<googleJavaFormat><version>1.36.1</version></googleJavaFormat>",
            "<artifactId>archunit-junit5</artifactId>",
            "<version>1.5.1</version>");
  }

  @Test
  void jdkAbove25WarnsInTheOutputAndThePlanWithoutWritingMore(@TempDir final Path root)
      throws IOException {
    PomToolConfiguratorCompatibilityTest.writePom(root, "<project><build/></project>");
    final String original = Files.readString(root.resolve(POM));
    final PomToolConfigurator configurator =
        new PomToolConfigurator(ignored -> snapshot(JAVA_27, Map.of()));
    final String warning =
        "Ward supports Java 8 to 25; the project builds on JDK 27, and Ward may not work with it.";

    final BuildFileChange change =
        configurator.apply(
            PomToolConfiguratorCompatibilityTest.project(root),
            List.of(gate("tests")),
            null,
            finding -> true);

    assertThat(change.warnings()).containsExactly(warning);
    assertThat(change.changed()).isFalse();
    assertThat(Files.readString(root.resolve(POM))).isEqualTo(original);
    assertThat(
            configurator
                .plan(
                    PomToolConfiguratorCompatibilityTest.project(root),
                    List.of(gate("tests")),
                    null,
                    true)
                .details())
        .containsExactly(warning);
  }

  private static String strictPom(final Path root, final int jdk) throws IOException {
    PomToolConfiguratorCompatibilityTest.writePom(
        root, "<project><modelVersion>4.0.0</modelVersion></project>");
    new PomToolConfigurator(ignored -> snapshot(jdk, Map.of()))
        .apply(
            PomToolConfiguratorCompatibilityTest.project(root),
            STRICT_GATES.stream().map(PomToolConfiguratorCompatibilityTest::gate).toList(),
            QualityPreset.STRICT,
            finding -> true);
    return Files.readString(root.resolve(POM));
  }
}
