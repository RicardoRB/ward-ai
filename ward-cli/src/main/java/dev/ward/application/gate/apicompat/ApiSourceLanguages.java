package dev.ward.application.gate.apicompat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;

/** The language of an api-compat source file, by its extension. */
final class ApiSourceLanguages {
  private static final String JAVA = ".java";
  private static final String KOTLIN = ".kt";
  private static final String RUST = ".rs";
  private static final String CSHARP = ".cs";

  private ApiSourceLanguages() {}

  /** Whether a file of a JVM or Cargo source root is read; C# files are found per project. */
  static boolean isSource(final String file) {
    return file.endsWith(JAVA) || file.endsWith(KOTLIN) || file.endsWith(RUST);
  }

  /** The language of {@code path}; anything else is Java. */
  static AnalysisLanguage of(final String path) {
    if (path.endsWith(KOTLIN)) {
      return AnalysisLanguage.KOTLIN;
    }
    if (path.endsWith(RUST)) {
      return AnalysisLanguage.RUST;
    }
    return path.endsWith(CSHARP) ? AnalysisLanguage.CSHARP : AnalysisLanguage.JAVA;
  }
}
