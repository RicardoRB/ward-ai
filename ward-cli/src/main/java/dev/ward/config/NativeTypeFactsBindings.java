package dev.ward.config;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.infrastructure.analysis.swift.NativeSwiftTypes;
import java.nio.file.Path;
import java.util.Map;

/**
 * Wires the native type-fact sources of one project, by language. Each native resolver (JVM #367,
 * TypeScript #368, Python #369, Swift #370) registers its language here, and until then that
 * language's {@code TYPES} rules are reported as not run.
 */
final class NativeTypeFactsBindings {
  private NativeTypeFactsBindings() {}

  /** The native type-fact sources of the project at {@code projectRoot}, by language. */
  static Map<AnalysisLanguage, TypeFacts> forProject(final Path projectRoot) {
    return Map.of(AnalysisLanguage.SWIFT, new NativeSwiftTypes(projectRoot));
  }
}
