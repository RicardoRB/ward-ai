package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A Turborepo with a Next.js app whose code sits in app/ and a Maven API that also has a
 * package.json. The API is Maven's project and gets no Node tooling.
 */
class TurborepoMixedInitTest {
  private static final String FIXTURE = "turborepo-mixed";
  private static final String WEB_PACKAGE = "apps/web/package.json";
  private static final String API_PACKAGE = "apps/api/package.json";

  @TempDir Path project;

  @Test
  void nextAppGetsItsChecksAndMavenApiGetsNoNodeTooling() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    final String apiBefore = fixture.read(API_PACKAGE);

    final NodeFixtureRun.Result init =
        fixture.ward(
            "init",
            "--preset",
            "strict",
            "--integration",
            "none",
            "--ci",
            "github",
            "--agent-rules",
            "--yes");

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read(WEB_PACKAGE))
        .contains("\"check-types\": \"tsc --noEmit --pretty false\"");
    assertThat(fixture.read("apps/web/vitest.config.mjs")).startsWith("// managed-by: ward");
    assertThat(fixture.read(API_PACKAGE)).isEqualTo(apiBefore);
    assertThat(project.resolve("apps/api/vitest.config.mjs")).doesNotExist();
    assertThat(project.resolve("apps/api/stryker.config.mjs")).doesNotExist();
    assertThat(Files.readString(project.resolve("apps/api/pom.xml")))
        .contains("<artifactId>api</artifactId>");
    assertThat(project.resolve("apps/api/ward.toml")).exists();
  }
}
