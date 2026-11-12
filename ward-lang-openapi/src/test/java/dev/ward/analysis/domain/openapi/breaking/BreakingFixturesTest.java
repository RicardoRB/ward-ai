package dev.ward.analysis.domain.openapi.breaking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the breaking-change fixtures of {@code ward-cli} on the rules of this module, so that its
 * own coverage counts them: the violating pair yields the expected positions of the rule, the
 * passing pair and the base against itself yield nothing.
 */
class BreakingFixturesTest {

  private static final Path ROOT =
      Path.of("..", "ward-cli", "src", "test", "resources", "breaking-rules", "openapi");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

  static Stream<String> fixtures() throws IOException {
    try (Stream<Path> directories = Files.list(ROOT)) {
      return directories.map(path -> path.getFileName().toString()).sorted().toList().stream();
    }
  }

  @Test
  void thereAreFixtures() throws IOException {
    assertThat(fixtures()).isNotEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void theViolatingPairYieldsTheExpectedPositions(final String name) throws IOException {
    final Path directory = ROOT.resolve(name);
    final List<String> positions =
        run(directory, "revision.yaml").stream()
            .filter(finding -> finding.ruleId().equals("ward:openapi/" + name))
            .map(
                finding ->
                    finding.at().anchor().startLine() + ":" + finding.at().anchor().startColumn())
            .toList();

    assertThat(positions)
        .containsExactlyElementsOf(
            Files.readAllLines(directory.resolve("expected.txt")).stream()
                .filter(line -> !line.isBlank())
                .toList());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void thePassingPairAndTheBaseAgainstItselfYieldNothing(final String name) throws IOException {
    final Path directory = ROOT.resolve(name);

    assertThat(run(directory, "passing.yaml")).isEmpty();
    assertThat(run(directory, "base.yaml")).isEmpty();
  }

  private static List<BreakingFinding> run(final Path directory, final String revision) {
    return BreakingRules.run(
        new DocumentPair(load(directory, "base.yaml"), load(directory, revision), TODAY));
  }

  private static OpenApiDocument load(final Path directory, final String file) {
    return OpenApiDocuments.load(
            file,
            path -> {
              try {
                return Optional.of(
                    Files.readString(directory.resolve(path), StandardCharsets.UTF_8));
              } catch (final IOException unreadable) {
                return Optional.empty();
              }
            })
        .document()
        .orElseThrow();
  }
}
