package dev.ward.config;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.infrastructure.analysis.jvm.NativeJvmTypes;
import dev.ward.infrastructure.analysis.swift.NativeSwiftTypes;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Wires the native type-fact sources of one project, by language. The JVM resolver (#367) answers
 * Java and Kotlin, and the Swift resolver (#370) answers Swift. The TypeScript and Python resolvers
 * (#368, #369) register here too, and until then those languages' {@code TYPES} rules are reported
 * as not run.
 */
final class NativeTypeFactsBindings {
  private NativeTypeFactsBindings() {}

  /**
   * The native type-fact sources of the project at {@code projectRoot}, by language.
   *
   * @param projectRoot the project root
   * @param classpathFile the project's {@code java-classpath} file, if it has one
   */
  static Map<AnalysisLanguage, TypeFacts> forProject(
      final Path projectRoot, final Optional<Path> classpathFile) {
    final Map<AnalysisLanguage, TypeFacts> sources = new EnumMap<>(AnalysisLanguage.class);
    new NativeJvmTypes(projectRoot)
        .facts(classpathFile)
        .ifPresent(
            facts -> {
              sources.put(AnalysisLanguage.JAVA, facts);
              sources.put(AnalysisLanguage.KOTLIN, facts);
            });
    sources.put(AnalysisLanguage.SWIFT, new NativeSwiftTypes(projectRoot));
    return Map.copyOf(sources);
  }
}
