package dev.ward.infrastructure.analysis.swift;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.swift.SwiftFileSummarizer;
import dev.ward.analysis.domain.swift.SwiftModuleResolver;
import dev.ward.analysis.domain.swift.SwiftProjectLayout;
import dev.ward.application.gate.DependencyAdapters;
import java.nio.file.Path;
import java.util.List;

/** Builds the Swift dependency adapter of a project from its SwiftPM manifest and Xcode projects. */
public final class SwiftDependencyAdapters implements DependencyAdapters {

  @Override
  public List<DependencySupport> forProject(final Path projectRoot, final List<SourceUnit> units) {
    if (units.stream().noneMatch(unit -> unit.language() == AnalysisLanguage.SWIFT)
        || !SwiftLayouts.isSwiftProject(projectRoot)) {
      return List.of();
    }
    final SwiftProjectLayout layout = SwiftLayouts.read(projectRoot);
    return List.of(
        new DependencySupport(
            AnalysisLanguage.SWIFT, new SwiftFileSummarizer(), new SwiftModuleResolver(layout)));
  }
}
