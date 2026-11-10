package dev.ward.analysis.frontend.antlr.format.ecma;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatFixtureChecks;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatSafety;
import dev.ward.analysis.domain.format.SourceFormatter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Runs every javascript fixture folder through the shared format checks. */
class JavaScriptFormatterFixturesTest {

  private static final Path ROOT = Path.of("src/test/resources/format/javascript");

  private static final SourceFormatter FORMATTER = new JavaScriptFormatter();
  private static final FormatSafety SAFETY = new FormatSafety(new JavaScriptFormatSyntax());
  private static final FormatOptions OPTIONS = FormatOptions.defaults(FORMATTER.options());

  static List<String> cases() throws IOException {
    try (Stream<Path> dirs = Files.list(ROOT)) {
      return dirs.map(path -> path.getFileName().toString()).sorted().toList();
    }
  }

  @ParameterizedTest
  @MethodSource("cases")
  void fixtureMatchesGoldenOutput(final String name) throws IOException {
    final Path dir = ROOT.resolve(name);
    final String input = Files.readString(dir.resolve("input.js"), StandardCharsets.UTF_8);
    final Path expectedFile = dir.resolve("expected.js");
    final Optional<String> expected =
        Files.exists(expectedFile)
            ? Optional.of(Files.readString(expectedFile, StandardCharsets.UTF_8))
            : Optional.empty();

    assertThat(new FormatFixtureChecks(FORMATTER, SAFETY, OPTIONS).problems(input, expected))
        .isEmpty();
  }

  @Test
  void fixtureFolderIsNotEmpty() throws IOException {
    assertThat(cases()).isNotEmpty();
  }
}
