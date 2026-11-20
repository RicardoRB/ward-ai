package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.application.gate.DependencyAdapters;
import dev.ward.infrastructure.analysis.dotnet.DotnetDependencyAdapters;
import dev.ward.infrastructure.analysis.node.NodeDependencyAdapters;
import dev.ward.infrastructure.analysis.python.PythonDependencyAdapters;
import dev.ward.infrastructure.analysis.rust.RustDependencyAdapters;
import dev.ward.infrastructure.analysis.swift.SwiftDependencyAdapters;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Combines the dependency adapters of every language that resolves dependencies. */
public final class ProjectDependencyAdapters implements DependencyAdapters {
  private final List<DependencyAdapters> languages;

  /**
   * Creates the combined adapters over {@code languages}, in order.
   *
   * @throws NullPointerException if a language adapter is missing
   */
  public ProjectDependencyAdapters(final List<DependencyAdapters> languages) {
    this.languages = List.copyOf(languages);
  }

  /**
   * Returns the adapters of every bundled language, the JVM last.
   *
   * @return the combined adapters
   */
  public static ProjectDependencyAdapters bundled() {
    return new ProjectDependencyAdapters(
        List.of(
            new NodeDependencyAdapters(),
            new PythonDependencyAdapters(),
            new RustDependencyAdapters(),
            new SwiftDependencyAdapters(),
            new DotnetDependencyAdapters(),
            new JvmDependencyAdapters()
        )
    );
  }

  @Override
  public List<DependencySupport> forProject(final Path projectRoot, final List<SourceUnit> units) {
    return forProject(projectRoot, units, Optional.empty());
  }

  @Override
  public List<DependencySupport> forProject(
      final Path projectRoot,
      final List<SourceUnit> units,
      final Optional<String> javaClasspath
  ) {
    final List<DependencySupport> supports = new ArrayList<>();
    for (final DependencyAdapters language : languages) {
      supports.addAll(language.forProject(projectRoot, units, javaClasspath));
    }
    return List.copyOf(supports);
  }
}
