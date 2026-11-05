package dev.ward.analysis.domain.format;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every formatter fixture under {@code format/<language>/<case>/}: {@code input.<ext>} and,
 * when the input must format, {@code expected.<ext>}. A case without {@code expected} must be
 * refused. A language formatter passes by adding fixtures; this class needs no change.
 */
class FormatterFixturesTest {

  private static final String FIXTURE_ROOT = "/format";
  private static final FormatterRegistry REGISTRY =
      new FormatterRegistry(Map.of(AnalysisLanguage.JAVA, new FakeJavaFormatter()));
  private static final Map<AnalysisLanguage, FormatSyntax> SYNTAX =
      Map.of(AnalysisLanguage.JAVA, new FakeFormatSyntax());

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("cases")
  void fixtureMeetsEveryCheck(
      final AnalysisLanguage language, final String caseName, final Path caseDir)
      throws IOException {
    final String extension = extension(language);
    final String input = Files.readString(caseDir.resolve("input." + extension));
    final Path expectedFile = caseDir.resolve("expected." + extension);
    final Optional<String> expected =
        Files.exists(expectedFile) ? Optional.of(Files.readString(expectedFile)) : Optional.empty();
    final SourceFormatter formatter = REGISTRY.find(language).orElseThrow();
    final FormatFixtureChecks checks =
        new FormatFixtureChecks(
            formatter,
            new FormatSafety(SYNTAX.get(language)),
            FormatOptions.defaults(formatter.options()));

    assertThat(checks.problems(input, expected)).isEmpty();
  }

  @Test
  void everyRegisteredLanguageHasFixtureCases() {
    assertThat(cases().count()).isPositive();
  }

  static Stream<Arguments> cases() {
    final List<Arguments> cases = new ArrayList<>();
    for (final AnalysisLanguage language : REGISTRY.languages()) {
      final Path dir = root().resolve(language.name().toLowerCase(Locale.ROOT));
      try (Stream<Path> children = Files.list(dir)) {
        children
            .sorted()
            .forEach(
                child -> cases.add(Arguments.of(language, child.getFileName().toString(), child)));
      } catch (IOException e) {
        throw new IllegalStateException("cannot list " + dir, e);
      }
    }
    return cases.stream();
  }

  private static Path root() {
    try {
      return Path.of(FormatterFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    } catch (java.net.URISyntaxException e) {
      throw new IllegalStateException("bad fixture root", e);
    }
  }

  private static String extension(final AnalysisLanguage language) {
    return switch (language) {
      case JAVA -> "java";
      case KOTLIN -> "kt";
      case TYPESCRIPT -> "ts";
      case JAVASCRIPT -> "js";
      case PYTHON -> "py";
      case SWIFT -> "swift";
    };
  }
}
