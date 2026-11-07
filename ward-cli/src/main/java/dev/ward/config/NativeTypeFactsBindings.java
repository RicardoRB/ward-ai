package dev.ward.config;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.infrastructure.analysis.jvm.NativeJvmTypes;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Wires the native type-fact sources of one project, by language. The JVM resolver (#367) answers
 * Java and Kotlin; the TypeScript, Python and Swift resolvers (#368 to #370) register here too, and
 * until then those languages' {@code TYPES} rules are reported as not run.
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
    final Optional<TypeFacts> jvm = new NativeJvmTypes(projectRoot).facts(classpathFile);
    return jvm.map(
            facts ->
                Map.of(
                    AnalysisLanguage.JAVA, facts,
                    AnalysisLanguage.KOTLIN, facts))
        .orElse(Map.of());
  }
}
