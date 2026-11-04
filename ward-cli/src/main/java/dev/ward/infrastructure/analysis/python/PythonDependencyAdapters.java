package dev.ward.infrastructure.analysis.python;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.python.PythonDependencyResolver;
import dev.ward.analysis.domain.python.PythonFileSummarizer;
import dev.ward.analysis.domain.python.PythonProjectLayout;
import dev.ward.application.gate.DependencyAdapters;
import java.nio.file.Path;
import java.util.List;

/** Builds the Python dependency adapters of a run from the project's uv, Poetry or src layout. */
public final class PythonDependencyAdapters implements DependencyAdapters {

  @Override
  public List<DependencySupport> forProject(final Path projectRoot, final List<SourceUnit> units) {
    if (units.stream().noneMatch(unit -> unit.language() == AnalysisLanguage.PYTHON)) {
      return List.of();
    }
    final PythonProjectLayout layout = PythonLayouts.read(projectRoot);
    return List.of(
        new DependencySupport(
            AnalysisLanguage.PYTHON,
            new PythonFileSummarizer(),
            new PythonDependencyResolver(layout)));
  }
}
