package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the Java edges against ArchUnit: the fixture is compiled, ArchUnit imports the classes,
 * and each class dependency between project files must be an edge of the ward graph, and back.
 */
class JavaResolutionReferenceTest {
  private static final String FIXTURES = "src/test/resources/fixtures/";
  private static final String MAIN = "src/main/java";
  private static final String TEST = "src/test/java";
  private static final String ARROW = " -> ";

  @TempDir Path classes;

  /**
   * Without a classpath, the fixtures whose dependencies are all named in source: the
   * java-resolution fixture, and the java-matrix sources, whose tests name junit.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "java-resolution",
        "java-matrix/sources/base",
        "java-matrix/sources/java17",
        "java-matrix/sources/java21"
      })
  void wardEdgesWithoutClasspathEqualTheArchUnitDependencies(final String fixture)
      throws IOException {
    final Path project = Path.of(FIXTURES, fixture);
    final Set<String> archUnit = archUnitEdges(project, projectFiles(project));

    assertThat(WardJavaEdges.of(project, Optional.empty())).isEqualTo(archUnit);
    assertThat(archUnit).isNotEmpty();
  }

  /** With a classpath, every fixture, including the one whose dependencies only inference shows. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "java-resolution",
        "java-resolution-inferred",
        "java-matrix/sources/base",
        "java-matrix/sources/java17",
        "java-matrix/sources/java21"
      })
  void wardEdgesWithClasspathEqualTheArchUnitDependencies(final String fixture) throws IOException {
    final Path project = Path.of(FIXTURES, fixture);
    final Set<String> archUnit = archUnitEdges(project, projectFiles(project));

    assertThat(WardJavaEdges.of(project, Optional.of(classpathFile().toString())))
        .isEqualTo(archUnit);
    assertThat(archUnit).isNotEmpty();
  }

  /**
   * Without a classpath, a dependency that only inference shows is missing, and nothing else is:
   * the documented gap that the classpath closes.
   */
  @Test
  void withoutClasspathTheInferredDependencyIsTheOnlyGap() throws IOException {
    final Path project = Path.of(FIXTURES, "java-resolution-inferred");
    final Set<String> archUnit = archUnitEdges(project, projectFiles(project));
    final Set<String> ward = WardJavaEdges.of(project, Optional.empty());

    assertThat(ward).isSubsetOf(archUnit);
    assertThat(archUnit).containsExactlyInAnyOrderElementsOf(withGap(ward));
  }

  private static Set<String> withGap(final Set<String> ward) {
    final Set<String> edges = new TreeSet<>(ward);
    edges.add(
        "src/main/java/shop/service/Report.java" + ARROW + "src/main/java/shop/domain/Order.java");
    return edges;
  }

  /** A classpath file holding the test runtime classpath, which the matrix sources compile with. */
  private Path classpathFile() throws IOException {
    final Path file = classes.resolveSibling("classpath.txt");
    Files.writeString(file, System.getProperty("java.class.path"));
    return file;
  }

  /** The project-root-relative source files, main and test, with {@code /} separators. */
  private static Set<String> projectFiles(final Path project) throws IOException {
    final Set<String> files = new TreeSet<>();
    for (final String root : List.of(MAIN, TEST)) {
      try (Stream<Path> walk = Files.walk(project.resolve(root))) {
        walk.filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> files.add(project.relativize(path).toString().replace('\\', '/')));
      }
    }
    return files;
  }

  /** Compiles the project and maps each ArchUnit dependency between two files to an edge. */
  private Set<String> archUnitEdges(final Path project, final Set<String> files)
      throws IOException {
    compile(project, files);
    final JavaClasses imported = new ClassFileImporter().importPath(classes);
    final Set<String> edges = new TreeSet<>();
    for (final JavaClass origin : imported) {
      for (final Dependency dependency : origin.getDirectDependenciesFromSelf()) {
        final Optional<String> from = sourceFile(origin.getName(), files);
        final Optional<String> to = sourceFile(dependency.getTargetClass().getName(), files);
        if (from.isPresent() && to.isPresent() && !from.get().equals(to.get())) {
          edges.add(from.get() + ARROW + to.get());
        }
      }
    }
    return edges;
  }

  /** The file that declares the top-level class of {@code className}, nested classes included. */
  private static Optional<String> sourceFile(final String className, final Set<String> files) {
    final int nested = className.indexOf('$');
    final String topLevel = nested < 0 ? className : className.substring(0, nested);
    final String relative = topLevel.replace('.', '/') + ".java";
    return List.of(MAIN, TEST).stream()
        .map(root -> root + "/" + relative)
        .filter(files::contains)
        .findFirst();
  }

  private void compile(final Path project, final Set<String> files) throws IOException {
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final List<String> arguments =
        new ArrayList<>(
            List.of(
                "-d",
                classes.toString(),
                "-nowarn",
                "-classpath",
                System.getProperty("java.class.path")));
    for (final String file : files) {
      arguments.add(project.resolve(file).toString());
    }
    assertThat(compiler.run(null, null, null, arguments.toArray(String[]::new))).isZero();
  }
}
