package dev.ward.config;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.infrastructure.analysis.jvm.JavaTypeSidecar;
import dev.ward.infrastructure.analysis.node.TypeScriptTypeSidecar;
import dev.ward.infrastructure.analysis.python.PythonStubFiles;
import dev.ward.infrastructure.analysis.python.PythonTypeSidecar;
import java.nio.file.Path;
import java.util.Map;

/** Wires the optional type sidecars of one project, by language. */
final class TypeSidecarBindings {
  private TypeSidecarBindings() {}

  /**
   * The type sidecars of the project at {@code projectRoot}. Each starts only when a selected rule
   * needs types for its language, so a project without such a rule never runs one. Python answers
   * in process, so its sidecar starts without a process.
   */
  static Map<AnalysisLanguage, TypeSidecar> forProject(
      final Path projectRoot, final String wardVersion, final LanguageFrontend frontend) {
    return Map.of(
        AnalysisLanguage.PYTHON,
        new PythonTypeSidecar(frontend, new PythonStubFiles(projectRoot)),
        AnalysisLanguage.JAVA,
        new JavaTypeSidecar(projectRoot, wardVersion),
        AnalysisLanguage.TYPESCRIPT,
        new TypeScriptTypeSidecar(projectRoot, wardVersion),
        AnalysisLanguage.JAVASCRIPT,
        new TypeScriptTypeSidecar(projectRoot, wardVersion));
  }
}
