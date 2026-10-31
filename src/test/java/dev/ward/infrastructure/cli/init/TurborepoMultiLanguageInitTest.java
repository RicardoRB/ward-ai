package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A Turborepo with a Next.js app, a Maven API and a Python service. Each non-Node member is
 * configured by its own toolchain, and the Turbo root keeps only the Node members' scripts.
 */
class TurborepoMultiLanguageInitTest {
  private static final String FIXTURE = "turborepo-multi-language";
  private static final String ROOT_PACKAGE = "package.json";

  @TempDir Path project;

  @Test
  void membersOfOtherLanguagesGetTheirOwnConfigurationAndRootStaysNodeOnly()
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);

    final NodeFixtureRun.Result init = fixture.ward("init", "--preset", "standard", "--yes");

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read("apps/web/package.json")).contains("\"check-types\":");
    assertThat(project.resolve("apps/api/ward.toml")).exists();
    assertThat(project.resolve("services/ingest/ward.toml")).exists();
    assertThat(fixture.read(ROOT_PACKAGE)).doesNotContain("mvnw").doesNotContain("pytest");
  }
}
