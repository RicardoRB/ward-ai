package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.application.gate.DependencyAdapters;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs the dependency adapters of several languages, one after another, for one run. */
public final class CompositeDependencyAdapters implements DependencyAdapters {

  private final List<DependencyAdapters> adapters;

  /**
   * Creates the composite of {@code adapters}, in order.
   *
   * @param adapters the adapters whose supports are concatenated
   */
  public CompositeDependencyAdapters(final List<DependencyAdapters> adapters) {
    this.adapters = List.copyOf(adapters);
  }

  @Override
  public List<DependencySupport> forProject(final Path projectRoot, final List<SourceUnit> units) {
    final List<DependencySupport> supports = new ArrayList<>();
    for (final DependencyAdapters adapter : adapters) {
      supports.addAll(adapter.forProject(projectRoot, units));
    }
    return List.copyOf(supports);
  }
}
