package dev.ward.config;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeFacts;
import java.nio.file.Path;
import java.util.Map;

/**
 * Wires the native type-fact sources of one project, by language. None is registered yet: each
 * native resolver (JVM #367, TypeScript #368, Python #369, Swift #370) registers its language here,
 * and until then that language's {@code TYPES} rules are reported as not run.
 */
final class NativeTypeFactsBindings {
  private NativeTypeFactsBindings() {}

  /** The native type-fact sources of the project at {@code projectRoot}, by language. */
  static Map<AnalysisLanguage, TypeFacts> forProject(final Path projectRoot) {
    return Map.of();
  }
}
