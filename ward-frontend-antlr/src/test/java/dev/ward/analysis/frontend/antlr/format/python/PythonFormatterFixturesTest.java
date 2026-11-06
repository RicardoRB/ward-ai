package dev.ward.analysis.frontend.antlr.format.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatFixtureChecks;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatSafety;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every Python formatter fixture under {@code format/python/<case>/}: {@code input.py} and,
 * when the input must format, {@code expected.py}. A case without {@code expected.py} must be
 * refused.
 */
class PythonFormatterFixturesTest {

  private static final String FIXTURE_ROOT = "/format/python";
  private static final String INPUT = "input.py";
  private static final String EXPECTED = "expected.py";
  private static final int MINIMUM_CASES = 12;
  private static final FormatOptions OPTIONS =
      new FormatOptions(
          Map.of(
              "indent-width",
              "4",
              "indent-style",
              "space",
              "line-width",
              "100",
              "max-blank-lines",
              "2"));

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void fixtureMeetsEveryCheck(final String caseName, final Path caseDir) throws IOException {
    final String input = Files.readString(caseDir.resolve(INPUT));
    final Path expectedFile = caseDir.resolve(EXPECTED);
    final Optional<String> expected =
        Files.exists(expectedFile) ? Optional.of(Files.readString(expectedFile)) : Optional.empty();
    final FormatFixtureChecks checks =
        new FormatFixtureChecks(
            new PythonFormatter(), new FormatSafety(new PythonFormatSyntax()), OPTIONS);

    assertThat(checks.problems(input, expected)).isEmpty();
  }

  @Test
  void everyFixtureFolderIsCovered() {
    assertThat(cases().count()).isGreaterThanOrEqualTo(MINIMUM_CASES);
  }

  static Stream<Arguments> cases() {
    try (Stream<Path> children = Files.list(root())) {
      final List<Path> dirs = children.filter(Files::isDirectory).sorted().toList();
      return dirs.stream().map(dir -> Arguments.of(dir.getFileName().toString(), dir));
    } catch (IOException e) {
      throw new IllegalStateException("cannot list the Python fixtures", e);
    }
  }

  private static Path root() {
    try {
      return Path.of(PythonFormatterFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("bad fixture root", e);
    }
  }
}
