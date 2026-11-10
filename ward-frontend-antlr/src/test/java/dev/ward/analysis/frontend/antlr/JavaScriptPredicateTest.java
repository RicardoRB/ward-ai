package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import org.junit.jupiter.api.Test;

/** Grammar predicates and actions for JavaScriptPredicate, exercised through the frontend. */
class JavaScriptPredicateTest {

  private static ParseResult parse(final AnalysisLanguage language, final String text) {
    return new AntlrLanguageFrontend().parse(new SourceUnit("sample", language, text));
  }

  @Test
  void octalLiteralIsAcceptedOutsideStrictMode() {
    assertThat(parse(AnalysisLanguage.JAVASCRIPT, "var x = 010;").diagnostics()).isEmpty();
  }

  @Test
  void octalLiteralIsRejectedInStrictMode() {
    assertThat(parse(AnalysisLanguage.JAVASCRIPT, "'use strict';\nvar x = 010;").diagnostics())
        .isNotEmpty();
  }

  @Test
  void blockInsideStrictModeKeepsStrictMode() {
    final String text = "'use strict';\nif (true) { var y = 1; }\nvar x = 010;";

    assertThat(parse(AnalysisLanguage.JAVASCRIPT, text).diagnostics()).isNotEmpty();
  }

  @Test
  void letIsAnIdentifierOutsideStrictMode() {
    assertThat(parse(AnalysisLanguage.JAVASCRIPT, "var let = 1;").diagnostics()).isEmpty();
  }
}
