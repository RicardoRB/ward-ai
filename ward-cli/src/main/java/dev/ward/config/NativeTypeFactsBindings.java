package dev.ward.config;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.python.PythonStubs;
import dev.ward.analysis.domain.python.PythonTypeFacts;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import dev.ward.infrastructure.analysis.python.PythonStubFiles;
import java.nio.file.Path;
import java.util.Map;

/**
 * Wires the native type-fact sources of one project, by language. Each native resolver (JVM #367,
 * TypeScript #368, Python #369, Swift #370) registers its language here; a language without one
 * keeps its {@code TYPES} rules reported as not run.
 */
final class NativeTypeFactsBindings {
  private NativeTypeFactsBindings() {}

  /**
   * The native type-fact sources of the project at {@code projectRoot}, by language. Python reads
   * its stubs from the project, the bundled stubs and the virtualenv; nothing runs in a process.
   */
  static Map<AnalysisLanguage, TypeFacts> forProject(
      final Path projectRoot, final LanguageFrontend frontend) {
    final PythonStubs stubs = new PythonStubs(frontend, new PythonStubFiles(projectRoot)::text);
    final TypeFacts python = new CachedTypeFacts(new PythonTypeFacts(frontend, stubs), "");
    return Map.of(AnalysisLanguage.PYTHON, python);
  }
}
