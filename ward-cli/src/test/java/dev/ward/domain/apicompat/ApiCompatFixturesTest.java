package dev.ward.domain.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.api.ApiSurfaceReader;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.types.JavaApiSurfaceReader;
import dev.ward.analysis.domain.jvm.types.KotlinApiSurfaceReader;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Compares every {@code baseline} and {@code revision} pair under {@code
 * src/test/resources/api-compat/{java,kotlin}} and checks the changes against {@code expected.txt}:
 * one {@code path:line:column rule message} line per change, in order. Regenerate the files with
 * {@code -Dward.apicompat.write=true}.
 */
class ApiCompatFixturesTest {
  private static final Path ROOT = Path.of("src/test/resources/api-compat");
  private static final String WRITE = "ward.apicompat.write";
  private static final String EXPECTED = "expected.txt";

  @Test
  void everyFixtureMatchesItsExpectedChanges() throws IOException {
    final Map<String, List<String>> actual = new TreeMap<>();
    final Map<String, List<String>> expected = new TreeMap<>();
    for (final String language : List.of("java", "kotlin")) {
      try (Stream<Path> directories = Files.list(ROOT.resolve(language))) {
        for (final Path fixture : directories.filter(Files::isDirectory).sorted().toList()) {
          final String name = language + "/" + fixture.getFileName();
          actual.put(name, changes(fixture));
          if (Boolean.getBoolean(WRITE)) {
            Files.writeString(
                fixture.resolve(EXPECTED),
                String.join("\n", actual.get(name)) + (actual.get(name).isEmpty() ? "" : "\n"));
          }
          expected.put(name, Files.readAllLines(fixture.resolve(EXPECTED)));
        }
      }
    }
    assertThat(actual).isNotEmpty().isEqualTo(expected);
  }

  private static List<String> changes(final Path fixture) throws IOException {
    return compare(fixture).stream().map(ApiCompatFixturesTest::line).toList();
  }

  /** The changes Ward reports between the {@code baseline} and {@code revision} of a fixture. */
  static List<ApiChange> compare(final Path fixture) throws IOException {
    return ApiComparison.compare(
        read(fixture.resolve("baseline")), read(fixture.resolve("revision")));
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
      final List<Path> paths = files.filter(Files::isRegularFile).sorted().toList();
      final List<ApiType> found = new ArrayList<>();
      found.addAll(read(side, paths, ".java", AnalysisLanguage.JAVA, new JavaApiSurfaceReader()));
      found.addAll(read(side, paths, ".kt", AnalysisLanguage.KOTLIN, new KotlinApiSurfaceReader()));
      return found;
    }
  }

  private static List<ApiType> read(
      final Path side,
      final List<Path> paths,
      final String extension,
      final AnalysisLanguage language,
      final ApiSurfaceReader reader) {
    final List<SourceUnit> units =
        paths.stream()
            .filter(path -> path.toString().endsWith(extension))
            .map(path -> unit(side, path, language))
            .toList();
    return units.isEmpty()
        ? List.of()
        : reader.read(
            units,
            new AntlrLanguageFrontend(),
            name -> ClassLoader.getSystemResource(name.replace('.', '/') + ".class") != null);
  }

  private static SourceUnit unit(
      final Path side, final Path path, final AnalysisLanguage language) {
    try {
      return new SourceUnit(
          side.relativize(path).toString().replace('\\', '/'), language, Files.readString(path));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
