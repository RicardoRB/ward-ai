package dev.ward.application.analysis;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.project.Toolchain;
import java.util.Optional;

/** Maps each analysis language to the existing toolchain that owns its build tools. */
public final class AnalysisLanguageToolchains {

  private AnalysisLanguageToolchains() {}

  /**
   * Returns the toolchain of a language.
   *
   * @param language the analysis language
   * @return the toolchain that handles the language, or empty for a language with no build tools
   */
  public static Optional<Toolchain> toolchainOf(final AnalysisLanguage language) {
    return switch (language) {
      case JAVA, KOTLIN -> Optional.of(Toolchain.JVM);
      case TYPESCRIPT, JAVASCRIPT -> Optional.of(Toolchain.NODE);
      case PYTHON -> Optional.of(Toolchain.PYTHON);
      case SWIFT -> Optional.of(Toolchain.SWIFT);
      case OPENAPI -> Optional.empty();
    };
  }
}
