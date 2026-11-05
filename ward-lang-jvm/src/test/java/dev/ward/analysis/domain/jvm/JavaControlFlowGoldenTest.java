package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.port.ControlFlowResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Compares the control-flow dump of every Java fixture with its committed {@code .cfg} golden. Run
 * with {@code -Dward.cfg.update=true} to rewrite the goldens for review.
 */
class JavaControlFlowGoldenTest {

  private static final Path FIXTURES = Path.of("src/test/resources/cfg/java");
  private static final String UPDATE_PROPERTY = "ward.cfg.update";

  static List<Path> fixtures() throws IOException {
    try (Stream<Path> files = Files.list(FIXTURES)) {
      return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
    }
  }

  @ParameterizedTest
  @MethodSource("fixtures")
  void dumpMatchesTheGolden(final Path fixture) throws IOException {
    final String source = Files.readString(fixture);
    final ControlFlowResult result = JavaFlowFixtures.lower(source);
    assertThat(result.failures()).isEmpty();
    final String dump = JavaFlowFixtures.dumps(result);
    final Path golden = Path.of(fixture.toString().replaceAll("\\.java$", ".cfg"));

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.writeString(golden, dump);
    }
    assertThat(dump).isEqualTo(Files.readString(golden));
  }
}
