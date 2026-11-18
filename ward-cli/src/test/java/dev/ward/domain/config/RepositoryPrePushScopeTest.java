package dev.ward.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

/** Which pushed files make this repository's test, coverage and crap gates run. */
class RepositoryPrePushScopeTest {
  private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

  private static boolean selects(final String check, final String key, final String path)
      throws IOException {
    final TomlTable paths =
        Toml.parse(Files.readString(ROOT.resolve("ward.toml"), StandardCharsets.UTF_8))
            .getTableOrEmpty("checks." + check + ".paths");
    return paths.getArrayOrEmpty(key).toList().stream()
        .map(String::valueOf)
        .anyMatch(pattern -> PathPattern.of(pattern).matches(path));
  }

  private static boolean runs(final String path) throws IOException {
    boolean all = true;
    for (final String check : List.of("tests", "coverage", "crap")) {
      all &= selects(check, "match", path) || selects(check, "full", path);
    }
    return all;
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
          "CHANGELOG.md",
          "docs/performance/x.md",
          "website/src/pages/index.astro",
          "website/public/logo.svg"
      }
  )
  @DisplayName("a push without code skips the test gates")
  void aPushWithoutCodeSkipsTheTestGates(final String path) throws IOException {
    // Given a push that changes only documentation or website files
    // When the repository's tests, coverage and crap checks select it
    final boolean selected = runs(path);

    // Then none of the three runs
    assertThat(selected).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
          "ward-cli/src/main/java/dev/ward/A.java",
          "pom.xml",
          "ward-cli/pom.xml",
          "ward-cli/src/test/resources/rules/a.txt",
          "ward-cli/src/main/resources/rules/a.toml",
          ".mvn/maven.config",
          "mvnw",
          "ward.toml",
          "ward-rules.toml",
          "ward-architecture.toml",
          ".editorconfig",
          "README.md",
          "AGENTS.md",
          "skills/ward-init/SKILL.md",
          ".claude-plugin/plugin.json",
          ".claude/settings.json",
          ".opencode/plugins/ward-edit-write.js",
          ".githooks/pre-push",
          ".github/workflows/verify.yml",
          "website/public/schemas/ward-rules.schema.json",
          "website/src/content/docs/commands/check.mdx",
          "compatibility/drivers/DRIVERS.ts",
          "install.sh",
          "install.ps1"
      }
  )
  @DisplayName("a push with code, build or configuration files runs the test gates")
  void aPushWithCodeRunsTheTestGates(final String path) throws IOException {
    // Given a push that changes a Java, POM, resource, wrapper or ward.toml file
    // When the repository's tests, coverage and crap checks select it
    final boolean selected = runs(path);

    // Then all three run
    assertThat(selected).isTrue();
  }

  @Test
  @DisplayName("the three test gates select the same files")
  void theThreeTestGatesSelectTheSameFiles() throws IOException {
    // Given the paths tables of the tests, coverage and crap checks
    // When their match and full lists are read
    final List<String> tests = patterns("tests");

    // Then coverage and crap list exactly the same patterns as tests
    assertThat(tests).isNotEmpty();
    assertThat(patterns("coverage")).isEqualTo(tests);
    assertThat(patterns("crap")).isEqualTo(tests);
  }

  private static List<String> patterns(final String check) throws IOException {
    final TomlTable paths =
        Toml.parse(Files.readString(ROOT.resolve("ward.toml"), StandardCharsets.UTF_8))
            .getTableOrEmpty("checks." + check + ".paths");
    return Stream.of("match", "full")
        .flatMap(key -> paths.getArrayOrEmpty(key).toList().stream())
        .map(String::valueOf)
        .toList();
  }

  @Test
  @DisplayName("only the preset duplicates test runs its cases concurrently")
  @Execution(ExecutionMode.SAME_THREAD)
  void onlyPresetDuplicatesRunsConcurrently() throws IOException, ClassNotFoundException {
    // Given the test resources of ward-cli
    final Properties properties = new Properties();
    try (InputStream stream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(
            "junit-platform.properties"
        )) {
      properties.load(stream);
    }

    // When the JUnit parallel settings are read
    final Class<?> duplicates =
        Class.forName("dev.ward.infrastructure.rule.catalog.PresetDuplicatesTest");

    // Then parallelism is on, the default is the same thread, and that class is concurrent
    assertThat(properties)
        .containsEntry("junit.jupiter.execution.parallel.enabled", "true")
        .containsEntry("junit.jupiter.execution.parallel.mode.default", "same_thread")
        .containsEntry("junit.jupiter.execution.parallel.mode.classes.default", "same_thread");
    assertThat(duplicates.getAnnotation(Execution.class).value())
        .isEqualTo(ExecutionMode.CONCURRENT);
  }
}
