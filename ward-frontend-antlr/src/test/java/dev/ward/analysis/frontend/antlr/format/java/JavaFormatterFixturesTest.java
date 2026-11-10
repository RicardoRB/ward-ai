package dev.ward.analysis.frontend.antlr.format.java;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatFixtureChecks;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatSafety;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every Java formatter fixture under {@code format/java/<case>/}: {@code input.java} and, when
 * the input must format, {@code expected.java}. A case without {@code expected.java} must be
 * refused.
 */
class JavaFormatterFixturesTest {

  private static final String FIXTURE_ROOT = "/format/java";
  private static final String INPUT = "input.java";
  private static final String EXPECTED = "expected.java";

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void fixtureMeetsEveryCheck(final String caseName, final Path caseDir) throws IOException {
    final String input = Files.readString(caseDir.resolve(INPUT));
    final Path expectedFile = caseDir.resolve(EXPECTED);
    final Optional<String> expected =
        Files.exists(expectedFile) ? Optional.of(Files.readString(expectedFile)) : Optional.empty();
    final JavaFormatter formatter = new JavaFormatter();
    final FormatFixtureChecks checks =
        new FormatFixtureChecks(
            formatter,
            new FormatSafety(new JavaFormatSyntax()),
            FormatOptions.defaults(formatter.options()));

    assertThat(checks.problems(input, expected)).isEmpty();
  }

  @Test
  void everyCaseHasAnInputFile() {
    assertThat(cases().count()).isPositive();
  }

  static Stream<Arguments> cases() {
    final List<Arguments> cases = new ArrayList<>();
    try (Stream<Path> children = Files.list(root())) {
      children
          .sorted()
          .forEach(child -> cases.add(Arguments.of(child.getFileName().toString(), child)));
    } catch (IOException e) {
      throw new IllegalStateException("cannot list the Java fixtures", e);
    }
    return cases.stream();
  }

  private static Path root() {
    try {
      return Path.of(JavaFormatterFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("bad fixture root", e);
    }
  }
}
