package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Story #619: shared Git hooks written by {@code ward init}, through the full CLI and real Git. */
@DisabledOnOs(OS.WINDOWS)
class SharedGitHooksScenariosTest extends WardCliIntegrationSupport {
  private static final String SHARED = "--shared-hooks";
  private static final String QUALITY_HOOKS = "--quality-hooks";
  private static final String MARKER = "# ward-owned: quality-hooks v1 begin";
  private static final String TEAMMATE_COMMAND = "git config core.hooksPath .githooks";

  @BeforeEach
  void configuredGitProject() throws IOException, InterruptedException {
    git("init", "-q");
    assertThat(
        run(INIT, "-C", project.toString(), GATE_OPTION, TESTS, INTEGRATION_OPTION, NONE, "--yes")
    )
        .isZero();
    out.getBuffer().setLength(0);
  }

  @Test
  @DisplayName("shared hooks on a configured project")
  void sharedHooksOnAConfiguredProject() throws IOException {
    // Given a project with ward.toml and no .githooks
    final String wardToml = Files.readString(project.resolve(WARD_TOML));
    assertThat(project.resolve(".githooks")).doesNotExist();

    // When I run ward init --quality-hooks with the shared-folder option
    final int exit = run(INIT, "-C", project.toString(), QUALITY_HOOKS, SHARED);

    // Then .githooks/pre-commit and .githooks/pre-push exist, are executable and hold Ward's sections
    assertThat(exit).isZero();
    for (final String hook : List.of("pre-commit", "pre-push")) {
      final Path file = project.resolve(".githooks").resolve(hook);
      assertThat(file).exists().isExecutable();
      assertThat(Files.readString(file)).contains(MARKER, "ward check --profile " + hook);
    }
    assertThat(Files.readString(project.resolve(WARD_TOML))).isEqualTo(wardToml);
    // And init prints git config core.hooksPath .githooks for teammates
    assertThat(out.toString()).contains(TEAMMATE_COMMAND);
  }

  @Test
  @DisplayName("a second run")
  void aSecondRun() throws IOException {
    // Given shared hooks written by init
    run(INIT, "-C", project.toString(), QUALITY_HOOKS, SHARED);
    final String commit = Files.readString(project.resolve(".githooks/pre-commit"));
    final String push = Files.readString(project.resolve(".githooks/pre-push"));
    final long modified = Files.getLastModifiedTime(
        project.resolve(".githooks/pre-push")
    ).toMillis();

    // When I run the same command again
    final int exit = run(INIT, "-C", project.toString(), QUALITY_HOOKS, SHARED);

    // Then no file changes
    assertThat(exit).isZero();
    assertThat(Files.readString(project.resolve(".githooks/pre-commit"))).isEqualTo(commit);
    assertThat(Files.readString(project.resolve(".githooks/pre-push"))).isEqualTo(push);
    assertThat(Files.getLastModifiedTime(project.resolve(".githooks/pre-push")).toMillis())
        .isEqualTo(modified);
  }

  @Test
  @DisplayName("a dry run of removal")
  void aDryRunOfRemoval() throws IOException {
    // Given installed quality hooks
    run(INIT, "-C", project.toString(), QUALITY_HOOKS);
    final String commit = Files.readString(project.resolve(".git/hooks/pre-commit"));
    final String push = Files.readString(project.resolve(".git/hooks/pre-push"));

    // When I run ward init --remove-quality-hooks --dry-run
    final int exit = run(INIT, "-C", project.toString(), "--remove-quality-hooks", "--dry-run");

    // Then no hook file changes
    assertThat(exit).isZero();
    assertThat(Files.readString(project.resolve(".git/hooks/pre-commit"))).isEqualTo(commit);
    assertThat(Files.readString(project.resolve(".git/hooks/pre-push"))).isEqualTo(push);
    assertThat(out.toString()).contains("pre-commit");
  }

  @Test
  @DisplayName("this repository")
  void thisRepository() throws IOException {
    // Given this repository's .githooks from #618
    final Path hooks = Files.createDirectories(project.resolve(".githooks"));
    Files.writeString(
        hooks.resolve("pre-commit"),
        "#!/bin/sh\n# Shared hooks of this repository.\n: \"${WARD_JOBS:=auto}\"\nexport WARD_JOBS\n"
            + "\n"
            + MARKER
            + "\nold ward body\n# ward-owned: quality-hooks v1 end\n"
            + "echo user-tail\n"
    );

    // When init refreshes the shared hooks
    final int exit = run(INIT, "-C", project.toString(), QUALITY_HOOKS, SHARED);

    // Then only Ward's sections are rewritten
    assertThat(exit).isZero();
    final String refreshed = Files.readString(hooks.resolve("pre-commit"));
    assertThat(refreshed)
        .startsWith("#!/bin/sh\n# Shared hooks of this repository.\n")
        .contains("export WARD_JOBS\n\n" + MARKER, "ward check --profile pre-commit")
        .doesNotContain("old ward body")
        .endsWith("# ward-owned: quality-hooks v1 end\necho user-tail\n");
  }

  @Test
  @DisplayName("init sets core.hooksPath only when the user agrees")
  void setsHooksPathOnlyWithAgreement() throws IOException, InterruptedException {
    // Given a clone whose core.hooksPath is unset
    // When init writes shared hooks without --yes
    run(INIT, "-C", project.toString(), QUALITY_HOOKS, SHARED);
    // Then it leaves core.hooksPath alone
    assertThat(git("config", "--local", "--get", "core.hooksPath")).isEmpty();

    // When init writes them with --yes
    run(INIT, "-C", project.toString(), QUALITY_HOOKS, SHARED, "--yes");
    // Then it sets core.hooksPath for this clone
    assertThat(git("config", "--local", "--get", "core.hooksPath")).isEqualTo(".githooks");

    // Given a hooks path that points somewhere else
    git("config", "--local", "core.hooksPath", "other-hooks");
    // When init writes shared hooks with --yes
    run(INIT, "-C", project.toString(), QUALITY_HOOKS, "--shared-hooks=team-hooks", "--yes");
    // Then the other value stays
    assertThat(git("config", "--local", "--get", "core.hooksPath")).isEqualTo("other-hooks");
  }

  private String git(final String... args) throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    final Process process =
        new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true).start();
    final String output = new String(process.getInputStream().readAllBytes()).strip();
    process.waitFor();
    return output;
  }
}
