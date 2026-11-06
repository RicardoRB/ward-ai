package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Compares the scope dump of every JavaScript and TypeScript fixture with its committed {@code
 * .scopes} golden. Run with {@code -Dward.scopes.update=true} to rewrite the goldens for review.
 */
class NodeScopeGoldenTest {

  private static final Path ROOT = Path.of("src/test/resources/scopes");
  private static final String UPDATE_PROPERTY = "ward.scopes.update";

  static List<Path> fixtures() throws IOException {
    try (Stream<Path> files = Files.walk(ROOT)) {
      return files
          .filter(path -> path.toString().endsWith(".js") || path.toString().endsWith(".ts"))
          .sorted()
          .toList();
    }
  }

  @ParameterizedTest
  @MethodSource("fixtures")
  void dumpMatchesTheGolden(final Path fixture) throws IOException {
    final AnalysisLanguage language =
        fixture.toString().endsWith(".ts")
            ? AnalysisLanguage.TYPESCRIPT
            : AnalysisLanguage.JAVASCRIPT;
    final String dump = NodeScopeFixtures.scopes(language, Files.readString(fixture)).dump();
    final Path golden = Path.of(fixture.toString().replaceAll("\\.(js|ts)$", ".scopes"));

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.writeString(golden, dump);
    }
    assertThat(dump).isEqualTo(Files.readString(golden));
  }
}
