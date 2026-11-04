package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.JavaDependencyResolver;
import dev.ward.analysis.domain.jvm.JavaFileSummarizer;
import dev.ward.analysis.domain.jvm.JavaSourceRoot;
import dev.ward.analysis.domain.port.DependencyResolver;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.application.gate.DependencyAdapters;
import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.analysis.jvm.JdtJavaDependencyResolver;
import dev.ward.infrastructure.analysis.jvm.JvmSourceLayouts;
import dev.ward.infrastructure.analysis.node.NodeDependencyAdapters;
import dev.ward.infrastructure.project.gradle.GradleKotlinModules;
import dev.ward.infrastructure.project.maven.MavenJavaModules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/**
 * Builds the dependency adapters of a project from the layout each toolchain reads. A toolchain in
 * {@link #WITH_LAYOUT_READER} gets its adapter from its layout; one in {@link #WITHOUT_SUPPORT}
 * contributes no edges. Every toolchain is in exactly one of the two sets, so a new toolchain must
 * be classified before the build passes.
 */
public final class ProjectDependencyAdapters implements DependencyAdapters {

  /** The toolchains whose layout reader builds a dependency adapter. */
  static final Set<Toolchain> WITH_LAYOUT_READER = EnumSet.of(Toolchain.JVM, Toolchain.NODE);

  /** The toolchains that have no dependency adapter yet, so they contribute no edges. */
  static final Set<Toolchain> WITHOUT_SUPPORT =
      EnumSet.of(Toolchain.PYTHON, Toolchain.GO, Toolchain.RUST, Toolchain.DOTNET, Toolchain.SWIFT);

  private final DependencyAdapters node = new NodeDependencyAdapters();

  /** Creates the adapter factory; it holds no state, so one instance serves every project. */
  @Inject
  public ProjectDependencyAdapters() {}

  @Override
  public List<DependencySupport> forProject(final Path projectRoot, final List<SourceUnit> units) {
    return forProject(projectRoot, units, Optional.empty());
  }

  /**
   * Returns the TypeScript and JavaScript adapters of the run and the Java adapter. With a
   * classpath file the Java adapter also resolves bindings, which needs the classpath; without it
   * the edges come from the names in the source alone.
   */
  @Override
  public List<DependencySupport> forProject(
      final Path projectRoot, final List<SourceUnit> units, final Optional<String> javaClasspath) {
    final List<DependencySupport> supports = new ArrayList<>(node.forProject(projectRoot, units));
    if (units.stream().anyMatch(unit -> unit.language() == AnalysisLanguage.JAVA)) {
      supports.add(javaSupport(projectRoot.toAbsolutePath().normalize(), javaClasspath));
    }
    return List.copyOf(supports);
  }

  private static DependencySupport javaSupport(
      final Path root, final Optional<String> javaClasspath) {
    final List<JavaSourceRoot> roots = JvmSourceLayouts.of(root, jvmModules(root));
    final DependencyResolver resolver =
        javaClasspath
            .<DependencyResolver>map(
                name ->
                    new JdtJavaDependencyResolver(
                        root,
                        JdtJavaDependencyResolver.classpathEntries(classpathFile(root, name)),
                        roots))
            .orElseGet(() -> new JavaDependencyResolver(roots));
    return new DependencySupport(AnalysisLanguage.JAVA, new JavaFileSummarizer(), resolver);
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
