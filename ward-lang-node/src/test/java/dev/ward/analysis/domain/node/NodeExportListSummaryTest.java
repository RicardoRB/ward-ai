package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reads the import-equals references and the export lists of a file from its tree. */
class NodeExportListSummaryTest {

  private static FileSummary summarize(final AnalysisLanguage language, final String text) {
    final SourceUnit unit = new SourceUnit("src/a.ts", language, text);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    assertThat(parsed.diagnostics()).isEmpty();
    return new NodeFileSummarizer().summarize(unit, parsed.tree());
  }

  private static List<String> references(final FileSummary summary) {
    return summary.imports().stream().map(Reference::specifier).toList();
  }

  @Test
  void recordsImportEqualsRequireAsARequireReference() {
    final FileSummary summary =
        summarize(AnalysisLanguage.TYPESCRIPT, "import g = require(\"./g\");\n");

    assertThat(references(summary)).containsExactly("require:./g");
  }

  @Test
  void exportsTheNamesOfTypeOnlyAndAliasedExportLists() {
    final FileSummary summary =
        summarize(
            AnalysisLanguage.TYPESCRIPT,
            """
            const a = 1;
            export type { T } from "./t";
            export { a as b, type C };
            """);

    assertThat(summary.exports().stream().map(Symbol::name).toList())
        .containsExactly("T", "b", "C");
  }
}
