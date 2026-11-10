package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;

/** Parses Kotlin source with the real frontend, for the ktlint check tests. */
final class KotlinSourceTrees {

  private static final String FILE = "Sample.kt";

  private KotlinSourceTrees() {}

  /** Returns the syntax tree of {@code text} as a Kotlin file named {@code Sample.kt}. */
  static SyntaxNode parse(final String text) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit(FILE, AnalysisLanguage.KOTLIN, text))
        .tree();
  }
}
