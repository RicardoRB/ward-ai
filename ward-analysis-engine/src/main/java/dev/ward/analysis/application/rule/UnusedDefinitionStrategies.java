package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.Optional;

/** The unused-definition strategy of each language that has unused-definition rules. */
final class UnusedDefinitionStrategies {

  private UnusedDefinitionStrategies() {}

  /**
   * The strategy of {@code language}, or empty when the language has no unused-definition rules.
   */
  static Optional<UnusedDefinitionStrategy> forLanguage(final AnalysisLanguage language) {
    return switch (language) {
      case PYTHON -> Optional.of(new PythonUnusedDefinitions());
      case SWIFT -> Optional.of(new SwiftUnusedDefinitions());
      default -> Optional.empty();
    };
  }
}
