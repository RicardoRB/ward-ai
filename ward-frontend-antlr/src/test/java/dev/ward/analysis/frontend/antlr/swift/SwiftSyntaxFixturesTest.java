package dev.ward.analysis.frontend.antlr.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.FormatSafety;
import dev.ward.analysis.frontend.antlr.format.swift.SwiftFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.swift.SwiftFormatter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every file under {@code syntax/swift/} is a construct of current Swift. Each must parse with no
 * syntax error, format to a result, and format to itself again.
 */
class SwiftSyntaxFixturesTest {

  private static final int MIN_FIXTURES = 16;
  private static final String FIXTURE_ROOT = "/syntax/swift";
  private static final SwiftFormatter FORMATTER = new SwiftFormatter();
  private static final FormatSafety SAFETY = new FormatSafety(new SwiftFormatSyntax());
  private static final FormatOptions OPTIONS = FormatOptions.defaults(FORMATTER.options());

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void parsesWithoutSyntaxErrors(final String name, final Path file) throws IOException {
    assertThat(syntaxErrors(Files.readString(file))).isZero();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void formatsToAStableResult(final String name, final Path file) throws IOException {
    final String input = Files.readString(file);
    final String golden = formattedText(input);
    assertThat(formattedText(golden)).isEqualTo(golden);
  }

  /** The formatted text, or the source itself when the formatter has nothing to change. */
  private static String formattedText(final String source) {
    final FormatResult result = SAFETY.formatSafely(FORMATTER, OPTIONS, source);
    if (result instanceof FormatResult.Formatted formatted) {
      return formatted.text();
    }
    assertThat(result).isInstanceOf(FormatResult.Unchanged.class);
    return source;
  }

  @Test
  void everyConstructHasAFixture() {
    assertThat(fixtures().count()).isGreaterThanOrEqualTo(MIN_FIXTURES);
  }

  static Stream<Arguments> fixtures() {
    final List<Arguments> cases = new ArrayList<>();
    try (Stream<Path> children = Files.list(root())) {
      children
          .filter(child -> child.getFileName().toString().endsWith(".swift"))
          .sorted()
          .forEach(child -> cases.add(Arguments.of(child.getFileName().toString(), child)));
    } catch (IOException e) {
      throw new IllegalStateException("cannot list the Swift syntax fixtures", e);
    }
    return cases.stream();
  }

  /** Counts the syntax errors the parser reports for the whole file. */
  private static int syntaxErrors(final String text) {
    final SwiftLexer lexer = new SwiftLexer(CharStreams.fromString(text));
    lexer.removeErrorListeners();
    final SwiftParser parser = new SwiftParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.top_level();
    return parser.getNumberOfSyntaxErrors();
  }

  private static Path root() {
    try {
      return Path.of(SwiftSyntaxFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("bad fixture root", e);
    }
  }
}
