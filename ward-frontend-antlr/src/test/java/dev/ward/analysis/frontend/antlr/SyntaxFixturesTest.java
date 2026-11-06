package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.FormatSafety;
import dev.ward.analysis.domain.format.SourceFormatter;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.format.java.JavaFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.java.JavaFormatter;
import dev.ward.analysis.frontend.antlr.format.kotlin.KotlinFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.kotlin.KotlinFormatter;
import dev.ward.analysis.frontend.antlr.format.python.PythonFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.python.PythonFormatter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parses every syntax fixture under {@code syntax/<language>/}. Each file must parse with no
 * diagnostic, format without refusal, and format to the same text a second time.
 */
class SyntaxFixturesTest {

  private static final String FIXTURE_ROOT = "/syntax";
  private static final int MINIMUM_FIXTURES = 8;

  private static final AntlrLanguageFrontend FRONTEND = new AntlrLanguageFrontend();

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void fixtureParsesWithoutDiagnostics(final String name, final AnalysisLanguage language, final Path file)
      throws IOException {
    final ParseResult result = FRONTEND.parse(unit(language, file));

    assertThat(result.diagnostics()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void fixtureFormatsToAStableResult(
      final String name, final AnalysisLanguage language, final Path file) throws IOException {
    final String input = Files.readString(file);
    final FormatSafety safety = safety(language);
    final SourceFormatter formatter = formatter(language);
    final FormatOptions options = FormatOptions.defaults(formatter.options());

    final String once = text(safety.formatSafely(formatter, options, input), input);
    final String twice = text(safety.formatSafely(formatter, options, once), once);

    assertThat(twice).isEqualTo(once);
  }

  @Test
  void everyLanguageHasFixtures() throws IOException {
    assertThat(fixtures().count()).isGreaterThanOrEqualTo(MINIMUM_FIXTURES);
  }

  static Stream<Arguments> fixtures() throws IOException {
    final List<Arguments> all = new java.util.ArrayList<>();
    for (final AnalysisLanguage language :
        List.of(AnalysisLanguage.JAVA, AnalysisLanguage.KOTLIN, AnalysisLanguage.PYTHON)) {
      final Path dir = root().resolve(folder(language));
      try (Stream<Path> files = Files.list(dir)) {
        files
            .sorted()
            .forEach(file -> all.add(Arguments.of(folder(language) + "/" + file.getFileName(), language, file)));
      }
    }
    return all.stream();
  }

  private static String text(final FormatResult result, final String fallback) {
    if (result instanceof FormatResult.Formatted formatted) {
      return formatted.text();
    }
    if (result instanceof FormatResult.Unchanged) {
      return fallback;
    }
    throw new AssertionError("refused: " + ((FormatResult.Refused) result).reason());
  }

  private static SourceUnit unit(final AnalysisLanguage language, final Path file) throws IOException {
    return new SourceUnit(file.getFileName().toString(), language, Files.readString(file));
  }

  private static SourceFormatter formatter(final AnalysisLanguage language) {
    return switch (language) {
      case JAVA -> new JavaFormatter();
      case KOTLIN -> new KotlinFormatter();
      default -> new PythonFormatter();
    };
  }

  private static FormatSafety safety(final AnalysisLanguage language) {
    return switch (language) {
      case JAVA -> new FormatSafety(new JavaFormatSyntax());
      case KOTLIN -> new FormatSafety(new KotlinFormatSyntax());
      default -> new FormatSafety(new PythonFormatSyntax());
    };
  }

  private static String folder(final AnalysisLanguage language) {
    return switch (language) {
      case JAVA -> "java";
      case KOTLIN -> "kotlin";
      default -> "python";
    };
  }

  private static Path root() {
    try {
      return Path.of(SyntaxFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("bad fixture root", e);
    }
  }
}
