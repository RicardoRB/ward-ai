package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
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
  void recordsPublicAndOpenDeclarationsAsExportsOfTheirKind() {
    final FileSummary summary =
        summarize(
            """
            public struct Cart {
                public func total() -> Int { 0 }
                public var count: Int = 0
                func hidden() {}
            }
            open class Base {}
            struct Private {}
            """);

    assertThat(summary.exports().stream().filter(symbol -> symbol.kind().startsWith("public:")))
        .extracting(Symbol::name, Symbol::kind)
        .containsExactlyInAnyOrder(
            tuple("Cart", SwiftFileSummarizer.PUBLIC_TYPE_KIND),
            tuple("total", SwiftFileSummarizer.PUBLIC_FUNCTION_KIND),
            tuple("count", SwiftFileSummarizer.PUBLIC_PROPERTY_KIND),
            tuple("Base", SwiftFileSummarizer.PUBLIC_TYPE_KIND));
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
    assertThat(
            summary.exports().stream().filter(symbol -> symbol.kind().isEmpty()).map(Symbol::name))
        .containsExactlyElementsOf(summary.declared().stream().map(Symbol::name).toList());
  }

  @Test
  void aFileWithoutImportsHasNoReferences() {
    assertThat(summarize("struct Empty {}").imports()).isEmpty();
  }

  @Test
  void recordsUsedIdentifiersButNotOwnDeclaredNamesOrImports() {
    final FileSummary summary =
        summarize(
            """
            import Shop
            public struct Cart {
                let service: OrderService
            }
            """);

    assertThat(names(summary)).containsExactly("name:service", "name:OrderService");
  }

  @Test
  void aNameThatAppearsOnlyInACommentIsNotAUse() {
    final FileSummary summary =
        summarize(
            """
            // OrderService is only mentioned here
            /* and OrderService here too */
            struct Cart {}
            """);

    assertThat(names(summary)).isEmpty();
  }

  @Test
  void aNameInAStringLiteralIsNotAUse() {
    final FileSummary summary =
        summarize(
            """
            struct Cart {
                let label = "OrderService"
            }
            """);

    assertThat(names(summary)).doesNotContain("name:OrderService");
  }

  private static List<String> names(final FileSummary summary) {
    return summary.imports().stream()
        .map(Reference::specifier)
        .filter(specifier -> specifier.startsWith(SwiftFileSummarizer.NAME_PREFIX))
        .toList();
  }

  private static FileSummary summarize(final String source) {
    final SourceUnit unit = new SourceUnit("Sample.swift", AnalysisLanguage.SWIFT, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    assertThat(parsed.diagnostics()).isEmpty();
    return new SwiftFileSummarizer().summarize(unit, parsed.tree());
  }
}
