package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What a user of the removed OpenSpec integration sees: the existing usage and config errors. */
@DisabledOnOs(OS.WINDOWS)
class WardCliRemovedOpenSpecTest extends WardCliIntegrationSupport {
  @Test
  void theSpecCommandGroupIsGone() {
    assertThat(run("spec", "validate")).isEqualTo(2);
    assertThat(err.toString()).contains("spec");
    assertThat(run("--help")).isZero();
    assertThat(out.toString()).doesNotContain(" spec ");
  }

  @ParameterizedTest
  @ValueSource(strings = {"--exclude-spec"})
  void checkRejectsTheRemovedOption(final String option) {
    assertThat(run(CHECK, option, "-C", project.toString())).isEqualTo(2);
    assertThat(err.toString()).contains(option);
  }

  @ParameterizedTest
  @ValueSource(strings = {"--tool", "--openspec-validation"})
  void initRejectsTheRemovedOptions(final String option) {
    assertThat(
            run(
                INIT,
                INTEGRATION_OPTION,
                NONE,
                GATE_OPTION,
                TESTS,
                option,
                "openspec",
                "-C",
                project.toString()))
        .isEqualTo(2);
    assertThat(err.toString()).contains(option);
    assertThat(project.resolve(WARD_TOML)).doesNotExist();
  }

  @Test
  void initRefusesToInstallOpenSpec() {
    assertThat(
            run(
                INIT,
                INTEGRATION_OPTION,
                NONE,
                GATE_OPTION,
                TESTS,
                "--install-tool",
                "openspec",
                "-C",
                project.toString()))
        .isEqualTo(2);
    assertThat(err.toString()).contains("--install-tool openspec requires --gate openspec.");
    assertThat(project.resolve(WARD_TOML)).doesNotExist();
  }

  @ParameterizedTest
  @ValueSource(strings = {"--guard-approved-specs", "--no-guard-approved-specs"})
  void integrateRejectsTheRemovedGuardOptions(final String option) {
    assertThat(run("integrate", CLAUDE_CODE, option, "-C", project.toString())).isEqualTo(2);
    assertThat(err.toString()).contains("guard-approved-specs");
  }

  @Test
  void theHooksRejectTheRemovedOptions() {
    assertThat(run("hook", CLAUDE_CODE, "--exclude-spec", "-C", project.toString())).isEqualTo(2);
    assertThat(run("hook", CLAUDE_CODE, "--guard-approved-specs", "-C", project.toString()))
        .isEqualTo(2);
    assertThat(
            run(
                "hook",
                "native",
                "--agent",
                "codex",
                "--guard-approved-specs",
                "-C",
                project.toString()))
        .isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"openspec", "openspec-archived", "openspec-junit-traceability"})
  void aRemovedCheckTypeIsAnUnknownType(final String type) throws IOException {
    Files.writeString(
        project.resolve(WARD_TOML),
        "version = 2\n[checks.spec]\nuses = \"" + type + "\"\ncommand = \"true\"\n");

    assertThat(run(CHECK, "-C", project.toString())).isEqualTo(2);
    assertThat(err.toString())
        .contains("'checks.spec.uses' must be one of the supported types: [")
        .contains("compile")
        .doesNotContain("openspec");
  }
}
