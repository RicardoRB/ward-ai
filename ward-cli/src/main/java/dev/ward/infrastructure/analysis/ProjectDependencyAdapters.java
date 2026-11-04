package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.python.PythonDependencyResolver;
import dev.ward.analysis.domain.python.PythonFileSummarizer;
import dev.ward.analysis.domain.python.PythonProjectLayout;
import dev.ward.application.gate.DependencyAdapters;
import dev.ward.infrastructure.analysis.python.PythonLayouts;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds the dependency adapters of a project from the layout readers of its toolchains. A
 * toolchain without a reader contributes no edges. Python is the first; each other toolchain adds
 * its reader here.
 */
public final class ProjectDependencyAdapters implements DependencyAdapters {

  @Override
  public List<DependencySupport> forProject(final Path projectRoot) {
    final PythonProjectLayout python = PythonLayouts.read(projectRoot);
    return List.of(
        new DependencySupport(
            AnalysisLanguage.PYTHON,
            new PythonFileSummarizer(),
            new PythonDependencyResolver(python)));
  }
}
