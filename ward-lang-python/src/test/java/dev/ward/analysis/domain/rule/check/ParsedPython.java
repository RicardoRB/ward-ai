package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;

/** Parses Python source with the real frontend for the tests of the rule checks. */
final class ParsedPython {

  private static final String PATH = "module.py";

  private ParsedPython() {}

  /** The syntax tree of {@code source}, as a module at {@code module.py}. */
  static SyntaxNode tree(final String source) {
    return tree(PATH, source);
  }

  /** The syntax tree of {@code source} in the file {@code path}. */
  static SyntaxNode tree(final String path, final String source) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit(path, AnalysisLanguage.PYTHON, source))
        .tree();
  }
}
