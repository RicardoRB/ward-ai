package dev.ward.analysis.frontend.antlr.format.kotlin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatFixtureChecks;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatSafety;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every Kotlin formatter fixture under {@code format/kotlin/<case>/}: {@code input.kt} (or
 * {@code input.kts}) and, when the input must format, the matching {@code expected} file. A case
 * without {@code expected} must be refused.
 */
class KotlinFormatterFixturesTest {

  private static final String FIXTURE_ROOT = "/format/kotlin";
  private static final String[] EXTENSIONS = {"kt", "kts"};
  private static final String INPUT = "input.";
  private static final String EXPECTED = "expected.";

  private static final KotlinFormatter FORMATTER = new KotlinFormatter();
  private static final FormatFixtureChecks CHECKS =
      new FormatFixtureChecks(
          FORMATTER,
          new FormatSafety(new KotlinFormatSyntax()),
          FormatOptions.defaults(FORMATTER.options()));

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void fixtureMeetsEveryCheck(final String caseName, final Path caseDir) throws IOException {
    final Path input = find(caseDir, INPUT);
    final Optional<Path> expected = findOptional(caseDir, EXPECTED, input);

    final Optional<String> golden =
        expected.isPresent() ? Optional.of(Files.readString(expected.get())) : Optional.empty();
    assertThat(CHECKS.problems(Files.readString(input), golden)).isEmpty();
  }

  @ParameterizedTest(name = "every case has an input: {0}")
  @MethodSource("cases")
  void caseHasInput(final String caseName, final Path caseDir) {
    assertThat(findOptional(caseDir, INPUT, null)).isPresent();
  }

  static Stream<Arguments> cases() throws IOException, URISyntaxException {
    final Path root = Path.of(KotlinFormatterFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    try (Stream<Path> children = Files.list(root)) {
      return children
          .sorted()
          .map(child -> Arguments.of(child.getFileName().toString(), child))
          .toList()
          .stream();
    }
  }

  private static Path find(final Path caseDir, final String stem) {
    return findOptional(caseDir, stem, null)
        .orElseThrow(() -> new IllegalStateException("no " + stem + " file in " + caseDir));
  }

  /** Finds the file with the stem and an extension, using the input's extension when given. */
  private static Optional<Path> findOptional(
      final Path caseDir, final String stem, final Path matching) {
    for (final String extension : EXTENSIONS) {
      if (matching != null && !matching.getFileName().toString().endsWith("." + extension)) {
        continue;
      }
      final Path candidate = caseDir.resolve(stem + extension);
      if (Files.exists(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }
}
