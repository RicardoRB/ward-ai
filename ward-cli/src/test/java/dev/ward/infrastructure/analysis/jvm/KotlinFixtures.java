package dev.ward.infrastructure.analysis.jvm;

import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** The mixed Java/Kotlin fixture and its committed ArchUnit golden edges. */
final class KotlinFixtures {
  /** The golden file: sorted {@code [from, to]} pairs of project-relative source files. */
  static final String GOLDEN = "archunit-edges.json";

  private static final String FIXTURE = "src/test/resources/fixtures/kotlin-resolution";
  private static final String ARROW = " -> ";
  private static final String SRC = "/src/";

  private KotlinFixtures() {}

  /** Returns the fixture directory, relative to the ward-cli module. */
  static Path project() {
    return Path.of(FIXTURE);
  }

  /** Returns the project-relative Kotlin and Java source files, with {@code /} separators. */
  static Set<String> projectFiles(final Path project) throws IOException {
    final Set<String> files = new TreeSet<>();
    try (Stream<Path> walk = Files.walk(project)) {
      walk.filter(Files::isRegularFile)
          .map(path -> project.relativize(path).toString().replace('\\', '/'))
          .filter(path -> path.contains(SRC))
          .filter(path -> path.endsWith(".kt") || path.endsWith(".java"))
          .forEach(files::add);
    }
    return files;
  }

  /** Returns the absolute paths of the project's source files with {@code suffix}, sorted. */
  static List<String> sourcesOf(final Path project, final String suffix) throws IOException {
    return projectFiles(project).stream()
        .filter(path -> path.endsWith(suffix))
        .map(path -> project.resolve(path).toAbsolutePath().toString())
        .toList();
  }

  /** Returns the Java source roots of the project, joined with the path separator. */
  static String javaRoots(final Path project) throws IOException {
    try (Stream<Path> walk = Files.walk(project)) {
      return walk.filter(Files::isDirectory)
          .filter(
              path ->
                  path.endsWith(Path.of("src", "main", "java"))
                      || path.endsWith(Path.of("src", "test", "java")))
          .map(path -> path.toAbsolutePath().toString())
          .sorted()
          .reduce((first, second) -> first + File.pathSeparator + second)
          .orElse("");
    }
  }

  /** Reads the golden pairs as {@code from -> to} strings. */
  static Set<String> goldenEdges(final Path golden) throws IOException {
    final Object parsed = new ForyJsonCodec().fromJson(Files.readString(golden), Object.class);
    final Set<String> edges = new TreeSet<>();
    for (final Object pair : (List<?>) parsed) {
      final List<?> ends = (List<?>) pair;
      edges.add(ends.get(0) + ARROW + ends.get(1));
    }
    return edges;
  }
}
