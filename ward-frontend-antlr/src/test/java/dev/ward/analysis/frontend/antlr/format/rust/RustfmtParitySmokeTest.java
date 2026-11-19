package dev.ward.analysis.frontend.antlr.format.rust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.FormatSafety;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs the real rustfmt named by {@code -Dward.parity.rustfmt} on the input of every Rust formatter
 * fixture with its default settings and compares its output with Ward's. A fixture whose output
 * differs is listed in {@code parity/rustfmt-differences.txt} with its reason, as {@code
 * case|reason}; a listed fixture that no longer differs fails the test. Skipped unless the property
 * is set.
 */
class RustfmtParitySmokeTest {
  private static final String BINARY = System.getProperty("ward.parity.rustfmt", "");
  private static final String FIXTURE_ROOT = "/format/rust";
  private static final String DIFFERENCES = "/parity/rustfmt-differences.txt";
  private static final String INPUT = "input.rs";
  private static final String EDITION = "2021";
  private static final long TIMEOUT_SECONDS = 60;
  private static final RustFormatter FORMATTER = new RustFormatter();
  private static final FormatSafety SAFETY = new FormatSafety(new RustFormatSyntax());

  @Test
  @DisplayName("the same output as rustfmt")
  void theSameOutputAsRustfmt() throws Exception {
    assumeTrue(!BINARY.isEmpty(), "set -Dward.parity.rustfmt=<rustfmt>");
    final Map<String, String> listed = listedDifferences();
    final List<String> problems = new ArrayList<>();
    for (final Path fixture : fixtures()) {
      final String name = fixture.getFileName().toString();
      final String input = Files.readString(fixture.resolve(INPUT));
      final String ward = ward(input);
      final String rustfmt = rustfmt(input);
      final boolean differs = rustfmt == null || ward == null || !rustfmt.equals(ward);
      if (rustfmt != null && ward != null && differs && !listed.containsKey(name)) {
        problems.add("differs from rustfmt without a reason: " + name);
      }
      if (!differs && listed.containsKey(name)) {
        problems.add("listed but equal to rustfmt: " + name);
      }
    }
    assertThat(problems).isEmpty();
  }

  private static String ward(final String input) {
    final FormatResult result =
        SAFETY.formatSafely(FORMATTER, FormatOptions.defaults(FORMATTER.options()), input);
    if (result instanceof FormatResult.Formatted formatted) {
      return formatted.text();
    }
    return result instanceof FormatResult.Unchanged ? input : null;
  }

  /** The output of rustfmt on a source read from standard input, or null when it refuses. */
  private static String rustfmt(final String input) throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(BINARY, "--edition", EDITION, "--emit", "stdout")
            .redirectErrorStream(false)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    try (var stdin = process.getOutputStream()) {
      stdin.write(input.getBytes(StandardCharsets.UTF_8));
    }
    final String output = new String(
        process.getInputStream().readAllBytes(),
        StandardCharsets.UTF_8
    );
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    return process.exitValue() == 0 ? output : null;
  }

  private static List<Path> fixtures() throws IOException, URISyntaxException {
    final Path root = Path.of(RustfmtParitySmokeTest.class.getResource(FIXTURE_ROOT).toURI());
    try (Stream<Path> children = Files.list(root)) {
      return children.sorted().toList();
    }
  }

  private static Map<String, String> listedDifferences() throws IOException, URISyntaxException {
    final Path file = Path.of(RustfmtParitySmokeTest.class.getResource(DIFFERENCES).toURI());
    final Map<String, String> listed = new TreeMap<>();
    for (final String line : Files.readAllLines(file)) {
      if (!line.isBlank() && !line.startsWith("#")) {
        final int bar = line.indexOf('|');
        listed.put(line.substring(0, bar).strip(), line.substring(bar + 1).strip());
      }
    }
    return listed;
  }
}
