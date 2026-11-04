package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.application.gate.DependencyAdapters;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

  @Override
  public List<DependencySupport> forProject(final Path projectRoot, final List<SourceUnit> units) {
    final List<DependencySupport> supports = new ArrayList<>();
    for (final DependencyAdapters language : languages) {
      supports.addAll(language.forProject(projectRoot, units));
    }
    return List.copyOf(supports);
  }
}
