package dev.ward.config;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.python.PythonStubs;
import dev.ward.analysis.domain.python.PythonTypeFacts;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import dev.ward.infrastructure.analysis.jvm.JavaTypeSidecar;
import dev.ward.infrastructure.analysis.node.TypeScriptTypeSidecar;
import dev.ward.infrastructure.analysis.python.PythonStubFiles;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

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
        pythonTypes(projectRoot, frontend),
        AnalysisLanguage.JAVA,
        new JavaTypeSidecar(projectRoot, wardVersion),
        AnalysisLanguage.TYPESCRIPT,
        new TypeScriptTypeSidecar(projectRoot, wardVersion),
        AnalysisLanguage.JAVASCRIPT,
        new TypeScriptTypeSidecar(projectRoot, wardVersion));
  }

  /**
   * The native Python type facts of the project, in process: no sidecar process and no separate
   * sidecar class. Stubs come from the project, the bundled stubs and the virtualenv.
   */
  private static TypeSidecar pythonTypes(final Path projectRoot, final LanguageFrontend parser) {
    final PythonStubs stubs = new PythonStubs(parser, new PythonStubFiles(projectRoot)::text);
    final TypeFacts facts = new CachedTypeFacts(new PythonTypeFacts(parser, stubs), "");
    return new TypeSidecar() {
      @Override
      public Optional<TypeFacts> start() {
        return Optional.of(facts);
      }

      @Override
      public void close() {
        // Nothing runs for Python, so there is nothing to stop.
      }
    };
  }
}
