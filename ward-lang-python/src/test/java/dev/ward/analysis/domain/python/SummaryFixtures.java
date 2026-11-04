package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Parses Python source and summarizes it, for the summarizer tests. */
final class SummaryFixtures {

  static final String PATH = "shop/controller/clean.py";

  private SummaryFixtures() {}

  static FileSummary summarize(final String source) {
    final SourceUnit unit = new SourceUnit(PATH, AnalysisLanguage.PYTHON, source);
    final SyntaxNode tree = new AntlrLanguageFrontend().parse(unit).tree();
    return new PythonFileSummarizer().summarize(unit, tree);
  }

  static List<String> specifiers(final FileSummary summary) {
    return summary.imports().stream().map(Reference::specifier).toList();
  }

  static List<String> names(final List<Symbol> symbols) {
    return symbols.stream().map(Symbol::name).toList();
  }
}
