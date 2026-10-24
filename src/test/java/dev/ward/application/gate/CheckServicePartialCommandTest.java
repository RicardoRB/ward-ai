package dev.ward.application.gate;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.SupportedQualityGates;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A partial table's {@code command} replaces the gate's command only for narrowed checks. */
class CheckServicePartialCommandTest {
  @TempDir Path dir;

  private final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

  @Test
  void narrowedCommandReplacesTheFullCommand() throws IOException {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("ward.toml"),
        """
        version = 1
        [quality-gates.prettier]
        command = ["npx", "prettier", "--check", "."]
        [quality-gates.prettier.partial]
        inputs = ["src/**/*.ts"]
        args = ["{paths}"]
        command = ["npx", "prettier", "--check"]
        """);
    Files.createDirectories(dir.resolve("src"));
    Files.writeString(dir.resolve("src/app.ts"), "export {};\n");

    CheckServiceFixtures.checks(executor)
        .checkPartial(dir, List.of(dir.resolve("src/app.ts")), SupportedQualityGates.specIds());
    CheckServiceFixtures.checks(executor).check(dir);

    assertThat(executor.commands)
        .containsExactly(
            List.of("npx", "prettier", "--check", "src/app.ts"),
            List.of("npx", "prettier", "--check", "."));
  }
}
