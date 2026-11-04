package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import org.junit.jupiter.api.Test;

class SwiftFileSummarizerTest {

  @Test
  void recordsEveryImportFormAsAModuleReference() {
    final FileSummary summary =
        summarize(
            """
            import Foundation
            @testable import Shop
            import struct Domain.Order
            """);

    assertThat(summary.imports().stream().map(Reference::specifier))
        .containsExactly("module:Foundation", "module:Shop", "module:Domain");
  }

  @Test
  void recordsDeclaredTypesQualifiedByTheirEnclosingType() {
    final FileSummary summary =
        summarize(
            """
            public struct Cart {
                enum Kind { case book }
                protocol Priced {}
            }
            typealias Items = [String]
            """);

    assertThat(summary.declared().stream().map(Symbol::name))
        .containsExactly("Cart", "Cart.Kind", "Cart.Priced", "Items");
    assertThat(summary.exports()).isEqualTo(summary.declared());
  }

  @Test
  void aFileWithoutImportsHasNoReferences() {
    assertThat(summarize("struct Empty {}").imports()).isEmpty();
  }

  private static FileSummary summarize(final String source) {
    final SourceUnit unit = new SourceUnit("Sample.swift", AnalysisLanguage.SWIFT, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    assertThat(parsed.diagnostics()).isEmpty();
    return new SwiftFileSummarizer().summarize(unit, parsed.tree());
  }
}
