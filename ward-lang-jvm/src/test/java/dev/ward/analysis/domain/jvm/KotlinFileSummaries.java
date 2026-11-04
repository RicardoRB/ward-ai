package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Summarizes Kotlin text for the summarizer tests, and reads the summaries back. */
final class KotlinFileSummaries {
  static final String PATH = "shop/service/Checkout.kt";

  private KotlinFileSummaries() {}

  static FileSummary summarize(final String text) {
    final SourceUnit unit = new SourceUnit(PATH, AnalysisLanguage.KOTLIN, text);
    return new KotlinFileSummarizer()
        .summarize(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }

  static List<String> specifiers(final FileSummary summary) {
    return summary.imports().stream().map(Reference::specifier).toList();
  }

  static List<String> names(final List<Symbol> symbols) {
    return symbols.stream().map(Symbol::name).toList();
  }
}
