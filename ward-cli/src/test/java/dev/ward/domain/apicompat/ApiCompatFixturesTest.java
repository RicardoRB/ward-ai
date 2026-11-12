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
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Compares every {@code baseline} and {@code revision} pair under {@code
 * src/test/resources/api-compat/java} and checks the changes against {@code expected.txt}: one
 * {@code path:line:column rule message} line per change, in order. Regenerate the files with {@code
 * -Dward.apicompat.write=true}.
 */
class ApiCompatFixturesTest {
  private static final Path ROOT = Path.of("src/test/resources/api-compat/java");
  private static final String WRITE = "ward.apicompat.write";

  @TestFactory
  Stream<DynamicTest> everyFixtureMatchesItsExpectedChanges() throws IOException {
    try (Stream<Path> directories = Files.list(ROOT)) {
      return directories
          .filter(Files::isDirectory)
          .sorted()
          .map(
              directory ->
                  DynamicTest.dynamicTest(
                      directory.getFileName().toString(), () -> check(directory)))
          .toList()
          .stream();
    }
  }

  private static void check(final Path fixture) throws IOException {
    final List<String> actual =
        ApiComparison.compare(read(fixture.resolve("baseline")), read(fixture.resolve("revision")))
            .stream()
            .map(ApiCompatFixturesTest::line)
            .toList();
    final Path expected = fixture.resolve("expected.txt");
    if (Boolean.getBoolean(WRITE)) {
      Files.writeString(expected, String.join("\n", actual) + (actual.isEmpty() ? "" : "\n"));
    }
    assertThat(actual).isEqualTo(Files.readAllLines(expected));
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
