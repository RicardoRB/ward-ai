package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Compares the control-flow dump of every TypeScript and JavaScript fixture with its committed
 * {@code .cfg} golden. Run with {@code -Dward.cfg.update=true} to rewrite the goldens for review.
 */
class NodeControlFlowGoldenTest {

  private static final String UPDATE_PROPERTY = "ward.cfg.update";

  static List<Path> fixtures() throws IOException {
    final List<Path> found = new ArrayList<>();
    found.addAll(list(Path.of("src/test/resources/cfg/typescript"), ".ts"));
    found.addAll(list(Path.of("src/test/resources/cfg/javascript"), ".js"));
    return found;
  }

  @ParameterizedTest
  @MethodSource("fixtures")
  void dumpMatchesTheGolden(final Path fixture) throws IOException {
    final String name = fixture.getFileName().toString();
    final AnalysisLanguage language =
        name.endsWith(".ts") ? AnalysisLanguage.TYPESCRIPT : AnalysisLanguage.JAVASCRIPT;
    final String source = Files.readString(fixture);
    final SourceUnit unit = new SourceUnit("src/" + name, language, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    assertThat(parsed.diagnostics()).isEmpty();
    final ControlFlowResult result = new NodeControlFlow().lower(unit, parsed.tree());
    assertThat(result.failures()).isEmpty();
    final String dump = NodeFlowFixtures.dumps(result);
    final Path golden = Path.of(fixture.toString().replaceAll("\\.(ts|js)$", ".cfg"));

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.writeString(golden, dump);
    }
    assertThat(dump).isEqualTo(Files.readString(golden));
  }

  @Test
  void everyFixtureHasAGolden() throws IOException {
    for (final Path fixture : fixtures()) {
      assertThat(Path.of(fixture.toString().replaceAll("\\.(ts|js)$", ".cfg"))).exists();
    }
  }

  private static List<Path> list(final Path directory, final String extension) throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(path -> path.toString().endsWith(extension)).sorted().toList();
    }
  }
}
