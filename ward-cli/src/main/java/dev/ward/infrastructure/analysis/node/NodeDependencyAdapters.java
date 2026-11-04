package dev.ward.infrastructure.analysis.node;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.infrastructure.node.NodeDependencySupports;
import dev.ward.application.gate.DependencyAdapters;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds the TypeScript and JavaScript dependency adapters of a project from its manifests. */
public final class NodeDependencyAdapters implements DependencyAdapters {

  @Override
  public List<DependencySupport> forProject(final Path projectRoot, final List<SourceUnit> units) {
    final Set<String> files =
        units.stream()
            .filter(unit -> isScript(unit.language()))
            .map(SourceUnit::path)
            .collect(Collectors.toUnmodifiableSet());
    if (files.isEmpty()) {
      return List.of();
    }
    return NodeDependencySupports.create(NodeLayouts.read(projectRoot, files));
  }

  private static boolean isScript(final AnalysisLanguage language) {
    return language == AnalysisLanguage.TYPESCRIPT || language == AnalysisLanguage.JAVASCRIPT;
  }
}
