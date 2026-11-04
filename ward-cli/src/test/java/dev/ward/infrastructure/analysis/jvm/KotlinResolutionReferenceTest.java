package dev.ward.infrastructure.analysis.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks the committed Kotlin golden edges against the real compiler. The mixed fixture is compiled
 * with {@code kotlinc} and {@code javac}, ArchUnit imports the classes, and each class dependency
 * between project files must be the committed edge. Skipped unless {@code -Dward.kotlinc.binary}
 * names a kotlinc executable of a distribution whose {@code lib/kotlin-stdlib.jar} is beside it.
 */
@EnabledIfSystemProperty(named = "ward.kotlinc.binary", matches = ".+")
class KotlinResolutionReferenceTest {
  private static final String ARROW = " -> ";
  private static final String PROPERTY = "ward.kotlinc.binary";
  private static final long COMPILE_TIMEOUT_MINUTES = 5;

  @TempDir Path classes;

  @Test
  void archUnitEdgesOfTheCompiledFixtureEqualTheCommittedGoldenEdges() throws Exception {
    final Path project = KotlinFixtures.project();
    compile(project);

    assertThat(archUnitEdges(KotlinFixtures.projectFiles(project)))
        .isEqualTo(KotlinFixtures.goldenEdges(project.resolve(KotlinFixtures.GOLDEN)));
  }

  /** Compiles the Kotlin sources with kotlinc, then the Java sources against them with javac. */
  private void compile(final Path project) throws IOException, InterruptedException {
    final String kotlinc = System.getProperty(PROPERTY);
    final Path stdlib =
        Path.of(kotlinc).toAbsolutePath().getParent().getParent().resolve("lib/kotlin-stdlib.jar");
    final List<String> kotlin = new ArrayList<>(List.of(kotlinc));
    kotlin.addAll(KotlinFixtures.sourcesOf(project, ".kt"));
    kotlin.addAll(
        List.of(
            "-Xjava-source-roots=" + KotlinFixtures.javaRoots(project),
            "-classpath",
            stdlib.toString(),
            "-d",
            classes.toString()));
    run(kotlin);

    final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    final List<String> java =
        new ArrayList<>(
            List.of(
                "-d",
                classes.toString(),
                "-nowarn",
                "-classpath",
                classes + File.pathSeparator + stdlib));
    java.addAll(KotlinFixtures.sourcesOf(project, ".java"));
    assertThat(javac.run(null, null, null, java.toArray(String[]::new))).isZero();
  }

  private static void run(final List<String> command) throws IOException, InterruptedException {
    final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(COMPILE_TIMEOUT_MINUTES, TimeUnit.MINUTES)).isTrue();
    assertThat(process.exitValue()).as("kotlinc output:%n%s", output).isZero();
  }

  /** Maps each ArchUnit dependency between two project files to an edge, as "from -> to". */
  private Set<String> archUnitEdges(final Set<String> files) throws IOException {
    final JavaClasses imported = new ClassFileImporter().importPath(classes);
    final Set<String> edges = new TreeSet<>();
    for (final JavaClass origin : imported) {
      for (final Dependency dependency : origin.getDirectDependenciesFromSelf()) {
        final Optional<String> from = sourceFile(origin, files);
        final Optional<String> to = sourceFile(dependency.getTargetClass(), files);
        if (from.isPresent() && to.isPresent() && !from.get().equals(to.get())) {
          edges.add(from.get() + ARROW + to.get());
        }
      }
    }
    return edges;
  }

  /**
   * The project file of a class: its package directory and the source file name the class records.
   * A nested class and a file facade record the file that declares them.
   */
  private static Optional<String> sourceFile(final JavaClass type, final Set<String> files) {
    final String name = type.getName();
    final int nested = name.indexOf('$');
    final String topLevel = nested < 0 ? name : name.substring(0, nested);
    final int dot = topLevel.lastIndexOf('.');
    final String directory = dot < 0 ? "" : topLevel.substring(0, dot).replace('.', '/') + "/";
    final String relative = directory + type.getSourceCodeLocation().getSourceFileName();
    return files.stream()
        .filter(file -> file.equals(relative) || file.endsWith("/" + relative))
        .findFirst();
  }
}
