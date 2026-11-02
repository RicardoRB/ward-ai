package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** `ward integrate` and `ward doctor` treat a nested project like `ward init` does. */
@DisabledOnOs(OS.WINDOWS)
class WardCliNestedIntegrateTest extends WardCliIntegrationSupport {
  private static final String CLAUDE = "claude-code";
  private static final String DOCTOR = "doctor";

  @TempDir Path repository;

  private Path api;

  @BeforeEach
  void nestedProject() throws IOException {
    Files.createDirectories(repository.resolve(".git"));
    api = Files.createDirectories(repository.resolve("apps/api"));
    Files.copy(project.resolve("pom.xml"), api.resolve("pom.xml"));
  }

  @Test
  void integrateInstallsAtTheRepositoryRootAndDoctorFindsIt() {
    assertThat(run(DOCTOR, "-C", api.toString())).as(out.toString()).isNotNegative();
    assertThat(out.toString()).contains("No supported agent integration configured");
    out.getBuffer().setLength(0);

    assertThat(run("integrate", CLAUDE, "-C", api.toString())).as(err.toString()).isZero();

    assertThat(repository.resolve(".claude/hooks/ward-edit-write-apps-api")).exists();
    assertThat(repository.resolve(".claude/settings.json")).exists();
    assertThat(api.resolve(".claude")).doesNotExist();
    out.getBuffer().setLength(0);
    run(DOCTOR, "-C", api.toString());
    assertThat(out.toString()).contains("claude-code=installed");

    assertThat(run("integrate", CLAUDE, "--remove", "-C", api.toString())).isZero();
    assertThat(repository.resolve(".claude/hooks/ward-edit-write-apps-api")).doesNotExist();
  }

  @ParameterizedTest
  @CsvSource({"codex,.codex/hooks.json,.codex", "junie,.junie/config.json,.junie"})
  void nativeHarnessIsSharedByEveryNestedProject(
      final String id, final String config, final String directory) throws IOException {
    final Path billing = Files.createDirectories(repository.resolve("services/billing"));
    Files.copy(project.resolve("pom.xml"), billing.resolve("pom.xml"));

    assertThat(run("integrate", id, "-C", api.toString())).as(err.toString()).isZero();
    final String installed = Files.readString(repository.resolve(config));
    assertThat(run("integrate", id, "-C", billing.toString())).as(err.toString()).isZero();

    assertThat(repository.resolve(config)).hasContent(installed);
    assertThat(repository.resolve(directory + "/hooks/ward.sh")).exists();
    assertThat(api.resolve(directory)).doesNotExist();
    assertThat(billing.resolve(directory)).doesNotExist();
    out.getBuffer().setLength(0);
    run(DOCTOR, "-C", billing.toString());
    assertThat(out.toString()).contains(id + "=installed");
  }
}
