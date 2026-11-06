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
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every file under {@code syntax/swift/} is a construct of current Swift. Each must parse with no
 * syntax error, format to a result, and format to itself again.
 */
class SwiftSyntaxFixturesTest {

  private static final String FIXTURE_ROOT = "/syntax/swift";
  private static final SwiftFormatter FORMATTER = new SwiftFormatter();
  private static final FormatSafety SAFETY = new FormatSafety(new SwiftFormatSyntax());
  private static final FormatOptions OPTIONS = FormatOptions.defaults(FORMATTER.options());

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void parsesWithoutSyntaxErrors(final String name, final Path file) throws IOException {
    assertThat(syntaxErrors(Files.readString(file))).isEmpty();
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
    assertThat(fixtures().count()).isGreaterThanOrEqualTo(16);
  }

  static Stream<Arguments> fixtures() {
    final List<Arguments> cases = new ArrayList<>();
    try (Stream<Path> children = Files.list(root())) {
      children
          .filter(child -> child.getFileName().toString().endsWith(".swift"))
          .sorted()
          .forEach(
              child ->
                  cases.add(
                      Arguments.of(
                          child.getFileName().toString(), child)));
    } catch (IOException e) {
      throw new IllegalStateException("cannot list the Swift syntax fixtures", e);
    }
    return cases.stream();
  }

  private static List<String> syntaxErrors(final String text) {
    final SwiftLexer lexer = new SwiftLexer(CharStreams.fromString(text));
    lexer.removeErrorListeners();
    final SwiftParser parser = new SwiftParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    final List<String> errors = new ArrayList<>();
    final BaseErrorListener listener =
        new BaseErrorListener() {
          @Override
          public void syntaxError(
              final Recognizer<?, ?> recognizer,
              final Object offendingSymbol,
              final int line,
              final int column,
              final String message,
              final RecognitionException exception) {
            errors.add(line + ":" + column + " " + message);
          }
        };
    lexer.addErrorListener(listener);
    parser.addErrorListener(listener);
    parser.top_level();
    return errors;
  }

  private static Path root() {
    try {
      return Path.of(SwiftSyntaxFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("bad fixture root", e);
    }
  }
}
