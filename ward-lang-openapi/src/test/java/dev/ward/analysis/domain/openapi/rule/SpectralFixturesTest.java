package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.openapi.LoadResult;
import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import dev.ward.analysis.domain.openapi.YamlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every rule over the fixtures in {@code ward-cli/src/test/resources/rules/openapi}: the
 * violating document reports the findings, with the messages, that {@code spectral lint} printed
 * for it (the {@code messages.txt} beside it, one {@code line:column}, a tab and the message per
 * finding), and the passing document reports none. The catalog contract test in ward-cli compares
 * the positions through the real engine; this test counts the rules in this module's coverage.
 */
class SpectralFixturesTest {

  private static final Path FIXTURES =
      Path.of("..", "ward-cli", "src", "test", "resources", "rules", "openapi");

  static Stream<String> rules() {
    return OpenApiRules.all().keySet().stream().sorted();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rules")
  void theViolatingDocumentReportsTheMessagesOfSpectralAndThePassingOneNothing(final String name)
      throws IOException {
    final Path directory = FIXTURES.resolve(name);
    assertThat(
            run(name, directory.resolve("violating.yaml")).stream()
                .map(
                    finding ->
                        finding.span().startLine()
                            + ":"
                            + finding.span().startColumn()
                            + "\t"
                            + finding.message())
                .sorted())
        .containsExactlyElementsOf(
            Files.readAllLines(directory.resolve("messages.txt")).stream().sorted().toList());
    assertThat(run(name, directory.resolve("passing.yaml"))).isEmpty();
  }

  @Test
  void everyFixtureDirectoryIsOfARegisteredRule() throws IOException {
    try (Stream<Path> directories = Files.list(FIXTURES)) {
      assertThat(directories.map(path -> path.getFileName().toString()).toList())
          .containsExactlyInAnyOrderElementsOf(OpenApiRules.all().keySet());
    }
  }

  private static List<OpenApiFinding> run(final String name, final Path file) throws IOException {
    final LoadResult loaded =
        OpenApiDocuments.fromTrees(
            "api.yaml", Map.of("api.yaml", YamlParser.parse(Files.readString(file)).tree()));
    return OpenApiRules.all().get(name).check(loaded.document().orElseThrow());
  }
}
