package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Configuration syntax cases share one reader fixture and are kept as one contract suite.
class TomlParsingTest {
  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @TempDir Path dir;

  @Test
  void parsesDefaultConfiguration() throws Exception {
    final WardConfiguration config =
        read(
            reader,
            dir,
            """
                version = 2

                [checks.tests]
                enabled = true
                command = "./mvnw test"
                """);

    assertThat(config.version()).isEqualTo(2);
    assertThat(config.checks()).containsOnlyKeys("tests");
    assertThat(config.checks().get("tests"))
        .isEqualTo(new GateConfiguration("tests", true, List.of("./mvnw", "test")));
  }

  @Test
  void parsesMultipleGatesAndOnlyReturnsEnabledOnes() throws Exception {
    final WardConfiguration config =
        read(
            reader,
            dir,
            """
                version = 2

                [checks.tests]
                enabled = true
                command = "./mvnw test"

                [checks.architecture]
                enabled = false
                command = "./mvnw test -Dtest=ArchitectureTest"

                [checks.sonar]
                enabled = false
                command = "sonar analyze agentic"
                """);

    assertThat(config.checks()).containsOnlyKeys("tests", "architecture", "sonar");
    assertThat(config.enabledGates()).containsOnlyKeys("tests");
    assertThat(config.checks().get("architecture").command())
        .containsExactly("./mvnw", "test", "-Dtest=ArchitectureTest");
  }

  @Test
  void acceptsCommandAsArray() throws Exception {
    final WardConfiguration config =
        read(
            reader,
            dir,
            """
                version = 2
                [checks.tests]
                command = ["./mvnw", "test", "-Dtest=A, B"]
                """);

    assertThat(config.checks().get("tests").enabled()).isTrue();
    assertThat(config.checks().get("tests").command())
        .containsExactly("./mvnw", "test", "-Dtest=A, B");
  }

  @Test
  void parsesPartialConfiguration() throws Exception {
    final WardConfiguration config =
        read(
            reader,
            dir,
            """
                version = 2
                [checks.checkstyle]
                command = ["./mvnw", "checkstyle:check"]
                [checks.checkstyle.paths]
                match = ["src/**/*.java"]
                full = ["config/checkstyle.xml"]
                args = ["-Dcheckstyle.includes={paths}"]
                item = "**/{name}"
                separator = ","
                """);

    assertThat(config.checks().get("checkstyle").partial().match())
        .containsExactly("src/**/*.java");
    assertThat(config.checks().get("checkstyle").partial().args())
        .containsExactly("-Dcheckstyle.includes={paths}");
  }

  @Test
  void rejectsPartialArgumentsWithoutInputs() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    """
                        version = 2
                        [checks.tests]
                        command = "echo test"
                        [checks.tests.paths]
                        args = ["{paths}"]
                        """))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("tests.paths.args")
        .hasMessageContaining("match");
  }

  @Test
  void rejectsInvalidPartialValues() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    """
                        version = 2
                        [checks.tests]
                        command = "echo test"
                        [checks.tests.paths]
                        inputs = "src/**/*.java"
                        """))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("tests.paths.inputs");
  }

  @Test
  void rejectsPartialItemWithoutPlaceholder() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    """
                        version = 2
                        [checks.tests]
                        command = "echo test"
                        [checks.tests.paths]
                        match = ["**"]
                        item = "**/Order.java"
                        """))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("tests.paths.item");
  }

  @Test
  void noGatesIsValid() throws Exception {
    assertThat(read(reader, dir, "version = 2").checks()).isEmpty();
  }

  @Test
  void missingFileMentionsInit() {
    assertThatThrownBy(() -> reader.read(dir.resolve("ward.toml")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("ward init");
  }
}
