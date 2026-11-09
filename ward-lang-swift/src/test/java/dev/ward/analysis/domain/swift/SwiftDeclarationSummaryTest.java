package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The declarations and uses the Swift summarizer records for the dead-code rules. */
class SwiftDeclarationSummaryTest {

  @Test
  void declaresOnlyFunctionsNoProtocolSuperclassOrAttributeCanFix() {
    final FileSummary summary =
        summarize(
            """
            func helper() {}
            public func exported() {}
            @objc func selectorTarget() {}
            override func overridden() {}
            struct Canvas: Drawable {
                func draw() {}
            }
            extension Canvas {
                func paint() {}
            }
            protocol Drawable {
                func draw()
            }
            struct Plain {
                func measure() {}
            }
            """);

    assertThat(functionNames(summary)).containsExactly("helper", "measure");
    assertThat(summary.exports().stream().map(Symbol::name)).doesNotContain("helper", "measure");
  }

  @Test
  void usedNamesHoldCallsButNotFunctionNamesOrFixedFunctionNames() {
    final FileSummary summary =
        summarize(
            """
            func helper() {}
            func caller() { helper() }
            struct Canvas: Drawable {
                func draw() {}
            }
            """);

    assertThat(summary.usedNames())
        .contains("helper", SwiftFileSummarizer.CONFORMANCE_PREFIX + "Drawable", "draw");
    assertThat(summary.usedNames()).doesNotContain("caller");
  }

  private static List<String> functionNames(final FileSummary summary) {
    return summary.declared().stream()
        .filter(symbol -> SwiftFileSummarizer.FUNCTION_KIND.equals(symbol.kind()))
        .map(Symbol::name)
        .toList();
  }

  @Test
  void declaresStoredPropertiesOfClosedTypesOnly() {
    final FileSummary summary =
        summarize(
            """
            struct Plain {
                private var count = 0
                var label: String { return "x" }
                @Published var shown = false
            }
            struct Named: Drawable {
                var hidden = 0
            }
            func free() {
                var local = 1
                local = 2
            }
            """);

    assertThat(
            summary.declared().stream().filter(s -> "property".equals(s.kind())).map(Symbol::name))
        .containsExactly("count");
  }

  @Test
  void recordsPlainAssignmentsAndKeepsCompoundAssignmentsAsReads() {
    final FileSummary summary =
        summarize(
            """
            struct Plain {
                private var written = 0
                private var counted = 0
                mutating func bump() {
                    written = 1
                    counted += 1
                }
            }
            """);

    assertThat(summary.assignedNames()).containsExactly("written");
    assertThat(summary.usedNames()).contains("counted").doesNotContain("written");
  }

  private static FileSummary summarize(final String source) {
    final SourceUnit unit = new SourceUnit("Sample.swift", AnalysisLanguage.SWIFT, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    assertThat(parsed.diagnostics()).isEmpty();
    return new SwiftFileSummarizer().summarize(unit, parsed.tree());
  }

  @Test
  void aProtocolUsedOnlyToConformIsRecordedAsConformanceNotAsUse() {
    final FileSummary summary =
        summarize(
            """
            protocol Drawable {
                func draw()
            }
            struct Canvas: Drawable {
                func draw() {}
            }
            """);

    assertThat(summary.usedNames())
        .contains(SwiftFileSummarizer.CONFORMANCE_PREFIX + "Drawable")
        .doesNotContain("Drawable");
    assertThat(
            summary.declared().stream().filter(s -> "protocol".equals(s.kind())).map(Symbol::name))
        .containsExactly("Drawable");
  }

  @Test
  void aProtocolUsedAsATypeKeepsAPlainUse() {
    final FileSummary summary =
        summarize(
            """
            protocol Drawable {}
            struct Canvas: Drawable {}
            let shape: Drawable = Canvas()
            """);

    assertThat(summary.usedNames()).contains("Drawable");
  }

  @Test
  void aPublicProtocolIsNotDeclaredAsAProtocolCandidate() {
    final FileSummary summary = summarize("public protocol Shown {}\n");

    assertThat(summary.declared().stream().filter(s -> "protocol".equals(s.kind()))).isEmpty();
  }
}
