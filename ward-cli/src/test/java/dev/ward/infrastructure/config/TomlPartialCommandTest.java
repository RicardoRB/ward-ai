package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The narrowed base command a partial table may set. */
class TomlPartialCommandTest {
  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @TempDir Path dir;

  @Test
  void parsesNarrowedPartialCommand() throws Exception {
    final WardConfiguration config =
        read(
            reader,
            dir,
            """
                version = 2
                [checks.prettier]
                command = ["npx", "prettier", "--check", "."]
                [checks.prettier.paths]
                match = ["**/*.ts"]
                args = ["{paths}"]
                command = ["npx", "prettier", "--check"]
                """);

    assertThat(config.checks().get("prettier").partial().command())
        .containsExactly("npx", "prettier", "--check");
  }

  @Test
  void rejectsNarrowedCommandWithoutArguments() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    """
                        version = 2
                        [checks.prettier]
                        command = "npx prettier --check ."
                        [checks.prettier.paths]
                        match = ["**/*.ts"]
                        command = ["npx", "prettier", "--check"]
                        """))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("prettier.paths.command")
        .hasMessageContaining("args");
  }

  @Test
  void rejectsNarrowedCommandWithBlankElements() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    """
                        version = 2
                        [checks.prettier]
                        command = "npx prettier --check ."
                        [checks.prettier.paths]
                        match = ["**/*.ts"]
                        args = ["{paths}"]
                        command = ["npx", " "]
                        """))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("prettier.paths.command");
  }
}
