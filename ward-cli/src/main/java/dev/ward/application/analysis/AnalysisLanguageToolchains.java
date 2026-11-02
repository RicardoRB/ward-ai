package dev.ward.application.analysis;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.project.Toolchain;

/** Maps each analysis language to the existing toolchain that owns its build tools. */
public final class AnalysisLanguageToolchains {

  private AnalysisLanguageToolchains() {}

  /**
   * Returns the toolchain of a language.
   *
   * @param language the analysis language
   * @return the toolchain that handles the language
   */
  public static Toolchain toolchainOf(final AnalysisLanguage language) {
    return switch (language) {
      case JAVA, KOTLIN -> Toolchain.JVM;
      case TYPESCRIPT, JAVASCRIPT -> Toolchain.NODE;
      case PYTHON -> Toolchain.PYTHON;
      case SWIFT -> Toolchain.SWIFT;
    };
  }
}
