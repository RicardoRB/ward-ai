package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.JavaFileSummarizer;
import dev.ward.analysis.domain.jvm.JavaSourceRoot;
import dev.ward.analysis.domain.jvm.JvmDependencyResolver;
import dev.ward.analysis.domain.jvm.KotlinFileSummarizer;
import dev.ward.analysis.domain.port.DependencyResolver;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.application.gate.DependencyAdapters;
import dev.ward.domain.config.WardException;
import dev.ward.infrastructure.analysis.jvm.JdtJavaDependencyResolver;
import dev.ward.infrastructure.analysis.jvm.JvmSourceLayouts;
import dev.ward.infrastructure.project.gradle.GradleKotlinModules;
import dev.ward.infrastructure.project.maven.MavenJavaModules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Builds the JVM dependency adapters of a run: the edges of the names in the Java and Kotlin
 * sources, plus the binding edges when the check names a classpath file. Both languages share one
 * resolver, so they see each other's declarations. A run without Java or Kotlin sources gets none.
 */
public final class JvmDependencyAdapters implements DependencyAdapters {

  /** Creates the adapter factory; it holds no state, so one instance serves every project. */
  @Inject
  public JvmDependencyAdapters() {}

  @Override
  public List<DependencySupport> forProject(final Path projectRoot, final List<SourceUnit> units) {
    return forProject(projectRoot, units, Optional.empty());
  }

  @Override
  public List<DependencySupport> forProject(
      final Path projectRoot, final List<SourceUnit> units, final Optional<String> javaClasspath) {
    if (units.stream().noneMatch(JvmDependencyAdapters::isJvm)) {
      return List.of();
    }
    final Path root = projectRoot.toAbsolutePath().normalize();
    final List<JavaSourceRoot> roots = JvmSourceLayouts.of(root, jvmModules(root));
    final DependencyResolver resolver =
        javaClasspath
            .<DependencyResolver>map(
                name ->
                    new JdtJavaDependencyResolver(
                        root,
                        JdtJavaDependencyResolver.classpathEntries(classpathFile(root, name)),
                        roots))
            .orElseGet(() -> new JvmDependencyResolver(roots));
    return List.of(
        new DependencySupport(AnalysisLanguage.JAVA, new JavaFileSummarizer(), resolver),
        new DependencySupport(AnalysisLanguage.KOTLIN, new KotlinFileSummarizer(), resolver));
  }

  private static boolean isJvm(final SourceUnit unit) {
    return unit.language() == AnalysisLanguage.JAVA || unit.language() == AnalysisLanguage.KOTLIN;
  }

  /**
   * Returns the classpath file the check names, which must be a readable file. A missing or
   * unreadable file is a configuration error that names the key and the file.
   */
  private static Path classpathFile(final Path root, final String name) {
    final Path file = root.resolve(name).normalize();
    if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
      throw new WardException("'java-classpath' does not name a readable file: " + name);
    }
    return file;
  }

  /**
   * Returns the project root and the Maven and Gradle modules that have Java sources. A build
   * module that is not listed by its aggregator is not read.
   */
  private static List<Path> jvmModules(final Path root) {
    final List<Path> modules = new ArrayList<>();
    modules.add(root);
    for (final Path pom : MavenJavaModules.of(root)) {
      modules.add(pom.getParent());
    }
    modules.addAll(GradleKotlinModules.of(root));
    return modules.stream().distinct().toList();
  }
}
