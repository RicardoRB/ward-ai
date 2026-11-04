package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.swift.SwiftFileSummarizer;
import dev.ward.analysis.domain.swift.SwiftModuleResolver;
import dev.ward.analysis.domain.swift.SwiftProjectLayout;
import dev.ward.application.gate.DependencyAdapters;
import dev.ward.infrastructure.analysis.swift.SwiftLayouts;
import java.nio.file.Path;
import java.util.List;

/** Builds the dependency adapters of each project from the layout files found at its root. */
public final class ProjectDependencyAdapters implements DependencyAdapters {

  @Override
  public List<DependencySupport> forProject(final Path projectRoot) {
    if (!SwiftLayouts.isSwiftProject(projectRoot)) {
      return List.of();
    }
    final SwiftProjectLayout layout = SwiftLayouts.read(projectRoot);
    return List.of(
        new DependencySupport(
            AnalysisLanguage.SWIFT, new SwiftFileSummarizer(), new SwiftModuleResolver(layout)));
  }
}
