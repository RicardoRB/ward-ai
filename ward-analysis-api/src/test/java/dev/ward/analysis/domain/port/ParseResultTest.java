package dev.ward.analysis.domain.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParseResultTest {

  private static final SyntaxNode TREE =
      new SyntaxNode("file", new Span(1, 1, 1, 1, 0, 0), Map.of(), List.of());
  private static final AnalysisFinding ERROR =
      new AnalysisFinding(
          "syntax", AnalysisSeverity.ERROR, "missing ';'", "A.java", 2, 19, null, List.of());

  @Test
  void copiesDiagnosticsOnConstruction() {
    final List<AnalysisFinding> diagnostics = new ArrayList<>(List.of(ERROR));
    final ParseResult result = new ParseResult(TREE, diagnostics);
    diagnostics.clear();

    assertThat(result.diagnostics()).containsExactly(ERROR);
  }

  @Test
  void returnsUnmodifiableDiagnostics() {
    final ParseResult result = new ParseResult(TREE, List.of(ERROR));

    assertThatThrownBy(() -> result.diagnostics().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsMissingTree() {
    assertThatThrownBy(() -> new ParseResult(null, List.of()))
        .isInstanceOf(NullPointerException.class);
  }
}
