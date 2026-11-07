package dev.ward.config;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.python.PythonStubs;
import dev.ward.analysis.domain.python.PythonTypeFacts;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import dev.ward.infrastructure.analysis.jvm.NativeJvmTypes;
import dev.ward.infrastructure.analysis.python.PythonStubFiles;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Wires the native type-fact sources of one project, by language. The JVM resolver (#367) answers
 * Java and Kotlin; the Python resolver (#369) answers Python; the TypeScript and Swift resolvers
 * (#368, #370) register here too. A language without a source keeps its {@code TYPES} rules
 * reported as not run.
 */
final class NativeTypeFactsBindings {
  private NativeTypeFactsBindings() {}

  /**
   * The native type-fact sources of the project at {@code projectRoot}, by language.
   *
   * @param projectRoot the project root
   * @param frontend the parser that Python stubs and sources are read with
   * @param classpathFile the project's {@code java-classpath} file, if it has one
   */
  static Map<AnalysisLanguage, TypeFacts> forProject(
      final Path projectRoot, final LanguageFrontend frontend, final Optional<Path> classpathFile) {
    final Map<AnalysisLanguage, TypeFacts> sources = new HashMap<>();
    new NativeJvmTypes(projectRoot)
        .facts(classpathFile)
        .ifPresent(
            facts -> {
              sources.put(AnalysisLanguage.JAVA, facts);
              sources.put(AnalysisLanguage.KOTLIN, facts);
            });
    final PythonStubs stubs = new PythonStubs(frontend, new PythonStubFiles(projectRoot)::text);
    sources.put(
        AnalysisLanguage.PYTHON, new CachedTypeFacts(new PythonTypeFacts(frontend, stubs), ""));
    return Map.copyOf(sources);
  }
}
