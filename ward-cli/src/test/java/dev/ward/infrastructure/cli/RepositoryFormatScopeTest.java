package dev.ward.infrastructure.cli;

import static dev.ward.infrastructure.cli.FormatTestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

/** The formatting scope of this repository: its {@code .editorconfig} and {@code ward.toml}. */
@DisabledOnOs(OS.WINDOWS)
class RepositoryFormatScopeTest extends WardCliIntegrationSupport {
  private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
  private static final String FORMAT = "format";
  private static final String CHECK_FLAG = "--check";
  private static final String EDITORCONFIG = ".editorconfig";
  private static final String TEST_RESOURCE = "src/test/resources/fixture/";
  private static final Set<String> SKIPPED_FOLDERS =
      Set.of("target", "node_modules", "website", "src/test/resources");
  private static final Map<String, String> RAW_SAMPLES =
      Map.of(
          "src/A.java",
          "class A{int x;}\n",
          "py/main.py",
          "def f(x):\n      return x\n",
          "web/app.ts",
          "const a:number=1\n",
          "web/app.js",
          "const b=2\n",
          "app/Main.kt",
          "package app\n\nclass Main {\nval x:Int=1\n}\n",
          "ios/App.swift",
          "struct App{let x:Int}\n"
      );

  @Test
  @DisplayName("every language is checked")
  void everyLanguageIsChecked() throws IOException {
    // Given this repository
    Files.copy(ROOT.resolve(EDITORCONFIG), project.resolve(EDITORCONFIG));
    for (final Map.Entry<String, String> sample : RAW_SAMPLES.entrySet()) {
      write(project, sample.getKey(), sample.getValue());
    }
    write(project, TEST_RESOURCE + "Raw.java", "class Raw{int x;}\n");
    write(project, TEST_RESOURCE + "raw.py", "def f(x):\n      return x\n");

    // When the format gate checks it
    final int status = run(FORMAT, CHECK_FLAG, "-C", project.toString());

    // Then it covers Java, Python, TypeScript, JavaScript, Kotlin and Swift
    final String report = out.toString() + err.toString();
    assertThat(status).isNotEqualTo(ExitCodes.OK);
    assertThat(RAW_SAMPLES.keySet()).allSatisfy(path -> assertThat(report).contains(path));
    assertThat(report).doesNotContain("src/test/resources");
    // And the check of ward.toml selects every one of those languages
    assertThat(formatMatchGlobs())
        .anySatisfy(glob -> assertThat(glob).contains("java"))
        .anySatisfy(glob -> assertThat(glob).contains("py"))
        .anySatisfy(glob -> assertThat(glob).contains("ts"))
        .anySatisfy(glob -> assertThat(glob).contains("js"))
        .anySatisfy(glob -> assertThat(glob).contains("kt"))
        .anySatisfy(glob -> assertThat(glob).contains("swift"));
  }

  @Test
  @DisplayName("formatted code still parses")
  void formattedCodeStillParses() throws IOException {
    // Given every non-test-resource file of those languages
    Files.copy(ROOT.resolve(EDITORCONFIG), project.resolve(EDITORCONFIG));
    final List<Path> files = sources();
    assertThat(files).isNotEmpty();
    for (final Path file : files) {
      write(project, ROOT.relativize(file).toString(), Files.readString(file));
    }

    // When ward format writes them
    final int written = run(FORMAT, "-C", project.toString());

    // Then each file still parses: a second run refuses none and finds nothing to change
    assertThat(err.toString()).doesNotContain("refused");
    assertThat(written).isEqualTo(ExitCodes.OK);
    assertThat(run(FORMAT, CHECK_FLAG, "-C", project.toString())).isEqualTo(ExitCodes.OK);
  }

  private static List<String> formatMatchGlobs() throws IOException {
    final TomlTable paths =
        Toml.parse(Files.readString(ROOT.resolve("ward.toml"), StandardCharsets.UTF_8))
            .getTableOrEmpty("checks.format.paths");
    return paths.getArrayOrEmpty("match").toList().stream().map(String::valueOf).toList();
  }

  private static List<Path> sources() throws IOException {
    try (Stream<Path> walk = Files.walk(ROOT)) {
      return walk.filter(Files::isRegularFile)
          .filter(RepositoryFormatScopeTest::isCandidate)
          .toList();
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private static boolean isCandidate(final Path file) {
    final String relative = ROOT.relativize(file).toString();
    final boolean hidden = relative.startsWith(".");
    return !hidden
        && SKIPPED_FOLDERS.stream().noneMatch(skipped -> relative.contains(skipped + "/"))
        && relative.matches(".*\\.(java|py|ts|js|mjs|kt|swift)$");
  }
}
