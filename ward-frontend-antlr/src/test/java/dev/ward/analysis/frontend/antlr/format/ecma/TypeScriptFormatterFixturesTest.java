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

/** Runs every typescript fixture folder through the shared format checks. */
class TypeScriptFormatterFixturesTest {

  private static final Path ROOT = Path.of("src/test/resources/format/typescript");

  private static final SourceFormatter FORMATTER = new TypeScriptFormatter();
  private static final FormatSafety SAFETY = new FormatSafety(new TypeScriptFormatSyntax());
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
    final String input = Files.readString(dir.resolve("input.ts"), StandardCharsets.UTF_8);
    final Path expectedFile = dir.resolve("expected.ts");
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
