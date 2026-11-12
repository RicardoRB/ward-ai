package dev.ward.domain.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.types.JavaApiSurfaceReader;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Compares every {@code baseline} and {@code revision} pair under {@code
 * src/test/resources/api-compat/java} and checks the changes against {@code expected.txt}: one
 * {@code path:line:column rule message} line per change, in order. Regenerate the files with {@code
 * -Dward.apicompat.write=true}.
 */
class ApiCompatFixturesTest {
  private static final Path ROOT = Path.of("src/test/resources/api-compat/java");
  private static final String WRITE = "ward.apicompat.write";
  private static final String EXPECTED = "expected.txt";

  @Test
  void everyFixtureMatchesItsExpectedChanges() throws IOException {
    final Map<String, List<String>> actual = new TreeMap<>();
    final Map<String, List<String>> expected = new TreeMap<>();
    try (Stream<Path> directories = Files.list(ROOT)) {
      for (final Path fixture : directories.filter(Files::isDirectory).sorted().toList()) {
        final String name = fixture.getFileName().toString();
        actual.put(name, changes(fixture));
        if (Boolean.getBoolean(WRITE)) {
          Files.writeString(
              fixture.resolve(EXPECTED),
              String.join("\n", actual.get(name)) + (actual.get(name).isEmpty() ? "" : "\n"));
        }
        expected.put(name, Files.readAllLines(fixture.resolve(EXPECTED)));
      }
    }
    assertThat(actual).isNotEmpty().isEqualTo(expected);
  }

  private static List<String> changes(final Path fixture) throws IOException {
    return ApiComparison.compare(
            read(fixture.resolve("baseline")), read(fixture.resolve("revision")))
        .stream()
        .map(ApiCompatFixturesTest::line)
        .toList();
  }

  private static String line(final ApiChange change) {
    return change.location().path()
        + ":"
        + change.location().line()
        + ":"
        + change.location().column()
        + " api-compat:"
        + change.kind()
        + " "
        + change.message();
  }

  private static List<ApiType> read(final Path side) throws IOException {
    try (Stream<Path> files = Files.walk(side)) {
      final List<SourceUnit> units =
          files
              .filter(path -> path.toString().endsWith(".java"))
              .sorted()
              .map(path -> unit(side, path))
              .toList();
      return new JavaApiSurfaceReader()
          .read(
              units,
              new AntlrLanguageFrontend(),
              name -> ClassLoader.getSystemResource(name.replace('.', '/') + ".class") != null);
    }
  }

  private static SourceUnit unit(final Path side, final Path path) {
    try {
      return new SourceUnit(
          side.relativize(path).toString().replace('\\', '/'),
          AnalysisLanguage.JAVA,
          Files.readString(path));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
