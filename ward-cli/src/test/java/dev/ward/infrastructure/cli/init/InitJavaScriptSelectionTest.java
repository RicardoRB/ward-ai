package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which JavaScript packages init treats as projects when it selects what to configure. */
class InitJavaScriptSelectionTest {
  private static final String[] TESTS_ONLY = {"--integration", "none", "--gate", "tests"};
  private static final String POM =
      "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>"
          + "<artifactId>demo</artifactId><version>1.0.0</version></project>";
  private static final String API = "apps/api";
  private static final String WARD_TOML = "ward.toml";
  private static final String PROJECT = "--project";

  @TempDir Path root;

  private InitCommandFixture init;

  @BeforeEach
  void monorepo() throws IOException {
    init = InitCommandFixture.repository(root);
    write("package.json", "{}");
    write("pnpm-lock.yaml", "");
    write(API + "/pom.xml", POM);
  }

  @Test
  void javaScriptProjectWithSourcesCanBeNamed() throws IOException {
    write("apps/web/package.json", "{}");
    write("apps/web/src/index.js", "module.exports = 1;\n");

    // This fixture edits only Maven builds, so the run stops after selecting the gates.
    init.run(
        "",
        "--integration",
        "none",
        "--preset",
        "standard",
        PROJECT,
        "apps/web",
        "--dry-run",
        "--yes");

    assertThat(init.output())
        .contains("Selected quality gate 'eslint'", "Selected quality gate 'vitest'")
        .doesNotContain("tsc", "not inside");
  }

  @Test
  void staticAssetsInsideAJavaProjectDoNotMakeTheRootAProject() throws IOException {
    write(API + "/src/main/resources/static/app.js", "console.log(1);\n");

    final int exit = init.run("", with("--yes"));

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("Discovered project " + API + ".");
    assertThat(root.resolve(WARD_TOML)).doesNotExist();
  }

  private static String[] with(final String... extra) {
    final String[] args = new String[TESTS_ONLY.length + extra.length];
    System.arraycopy(TESTS_ONLY, 0, args, 0, TESTS_ONLY.length);
    System.arraycopy(extra, 0, args, TESTS_ONLY.length, extra.length);
    return args;
  }

  private void write(final String relative, final String content) throws IOException {
    final Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
