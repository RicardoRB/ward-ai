package dev.ward.analysis.frontend.antlr.format.csharp;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every C# formatter fixture under {@code format/csharp/<case>/}: {@code input.cs} and, when
 * the input must format, {@code expected.cs}. The expected files were written by {@code dotnet
 * format whitespace} at default settings (see {@code DotnetFormatParitySmokeTest}), except the
 * cases that {@code parity/dotnet-format-approximations.txt} lists. A case without {@code
 * expected.cs} must be refused.
 */
class CSharpFormatterFixturesTest {
  private static final String FIXTURE_ROOT = "/format/csharp";
  private static final CSharpFormatter FORMATTER = new CSharpFormatter();
  private static final FormatFixtureChecks CHECKS =
      new FormatFixtureChecks(
          FORMATTER,
          new FormatSafety(new CSharpFormatSyntax()),
          FormatOptions.defaults(FORMATTER.options())
      );

  @DisplayName("the same output as `dotnet format`")
  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void fixtureMeetsEveryCheck(final String caseName, final Path caseDir) throws IOException {
    final Path expected = caseDir.resolve("expected.cs");
    final Optional<String> golden =
        Files.exists(expected) ? Optional.of(Files.readString(expected)) : Optional.empty();

    assertThat(CHECKS.problems(Files.readString(caseDir.resolve("input.cs")), golden)).isEmpty();
  }

  static Stream<Arguments> cases() throws IOException, URISyntaxException {
    final Path root = Path.of(CSharpFormatterFixturesTest.class.getResource(FIXTURE_ROOT).toURI());
    try (Stream<Path> children = Files.list(root)) {
      return children
          .sorted()
          .map(child -> Arguments.of(child.getFileName().toString(), child))
          .toList()
          .stream();
    }
  }
}
