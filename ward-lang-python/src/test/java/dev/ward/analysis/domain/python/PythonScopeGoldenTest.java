package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.scope.ScopeDump;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Compares the scope dump of every Python fixture with its committed {@code .scopes} golden. Run
 * with {@code -Dward.scopes.update=true} to rewrite the goldens for review.
 */
class PythonScopeGoldenTest {

  private static final Path FIXTURES = Path.of("src/test/resources/scopes/python");
  private static final String UPDATE_PROPERTY = "ward.scopes.update";

  static List<Path> fixtures() throws IOException {
    try (Stream<Path> files = Files.list(FIXTURES)) {
      return files.filter(path -> path.toString().endsWith(".py")).sorted().toList();
    }
  }

  @ParameterizedTest
  @MethodSource("fixtures")
  void dumpMatchesTheGolden(final Path fixture) throws IOException {
    final String dump = ScopeDump.of(PythonScopeFixtures.scopes(Files.readString(fixture)));
    final Path golden = Path.of(fixture.toString().replaceAll("\\.py$", ".scopes"));

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.writeString(golden, dump);
    }
    assertThat(dump).isEqualTo(Files.readString(golden));
  }
}
