package dev.ward.analysis.frontend.antlr.format.rust;

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
 * Runs every Rust formatter fixture under {@code format/rust/<case>/}: {@code input.rs} and,
 * when the input must format, {@code expected.rs}. A case without {@code expected} must be
 * refused.
 */
class RustFormatterFixturesTest {
  private static final String FIXTURE_ROOT = "/format/rust";
  private static final String INPUT = "input.rs";
  private static final String EXPECTED = "expected.rs";
  private static final RustFormatter FORMATTER = new RustFormatter();
  private static final FormatFixtureChecks CHECKS =
      new FormatFixtureChecks(
          FORMATTER,
          new FormatSafety(new RustFormatSyntax()),
          FormatOptions.defaults(FORMATTER.options())
      );

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void fixtureMeetsEveryCheck(final String caseName, final Path caseDir) throws IOException {
    final String input = Files.readString(caseDir.resolve(INPUT));
    final Path expectedFile = caseDir.resolve(EXPECTED);
    final Optional<String> expected =
        Files.exists(expectedFile) ? Optional.of(Files.readString(expectedFile)) : Optional.empty();
    assertThat(CHECKS.problems(input, expected)).isEmpty();
  }

  @Test
  void everyFixtureHasAnInput() {
    assertThat(cases().count()).isPositive();
  }

  static Stream<Arguments> cases() {
    final List<Arguments> cases = new ArrayList<>();
    try (Stream<Path> children = Files.list(root())) {
      children
          .sorted()
          .forEach(child -> cases.add(Arguments.of(child.getFileName().toString(), child)));
    } catch (IOException e) {
      throw new IllegalStateException("cannot list the Rust fixtures", e);
    }
    return cases.stream();
  }

  private static Path root() {
    try {
      return Path.of(RustFormatterFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("bad fixture root", e);
    }
  }
}
