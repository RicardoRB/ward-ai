package dev.ward.config;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.python.PythonStubs;
import dev.ward.analysis.domain.python.PythonTypeFacts;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import dev.ward.infrastructure.analysis.jvm.NativeJvmTypes;
import dev.ward.infrastructure.analysis.node.NativeNodeTypes;
import dev.ward.infrastructure.analysis.python.PythonStubFiles;
import dev.ward.infrastructure.analysis.swift.NativeSwiftTypes;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Wires the native type-fact sources of one project, by language. The JVM resolver (#367) answers
 * Java and Kotlin, the Swift resolver (#370) answers Swift, the Python resolver (#369) answers
 * Python, and the Node resolver (#368) answers TypeScript and JavaScript.
 */
final class NativeTypeFactsBindings {
  private NativeTypeFactsBindings() {}

  /**
   * The native type-fact sources of the project at {@code projectRoot}, by language.
   *
   * @param projectRoot the project root
   * @param frontend the parser that every language reads sources with, shared with the other passes
   * @param classpathFile the project's {@code java-classpath} file, if it has one
   * @param jdkHome the JDK to read JDK types from; without one Java and Kotlin get no source, so
   *     their rules that need types are reported as not run
   */
  static Map<AnalysisLanguage, TypeFacts> forProject(
      final Path projectRoot,
      final LanguageFrontend frontend,
      final Optional<Path> classpathFile,
      final Optional<Path> jdkHome
  ) {
    final Map<AnalysisLanguage, TypeFacts> sources = new EnumMap<>(AnalysisLanguage.class);
    jdkHome
        .flatMap(
            home -> new NativeJvmTypes(projectRoot, frontend, Optional.of(home)).facts(
                classpathFile
            )
        )
        .ifPresent(
            facts -> {
              sources.put(AnalysisLanguage.JAVA, facts);
              sources.put(AnalysisLanguage.KOTLIN, facts);
            }
        );
    final TypeFacts node = new NativeNodeTypes(projectRoot, frontend).facts();
    sources.put(AnalysisLanguage.TYPESCRIPT, node);
    sources.put(AnalysisLanguage.JAVASCRIPT, node);
    sources.put(AnalysisLanguage.SWIFT, new NativeSwiftTypes(projectRoot, frontend));
    final PythonStubs stubs = new PythonStubs(frontend, new PythonStubFiles(projectRoot)::text);
    sources.put(
        AnalysisLanguage.PYTHON,
        new CachedTypeFacts(new PythonTypeFacts(frontend, stubs), "")
    );
    return Map.copyOf(sources);
  }
}
