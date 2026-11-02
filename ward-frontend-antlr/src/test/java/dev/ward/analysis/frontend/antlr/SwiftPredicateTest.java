package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import org.junit.jupiter.api.Test;

/** Grammar predicates and actions for SwiftPredicate, exercised through the frontend. */
class SwiftPredicateTest {

  private static ParseResult parse(final AnalysisLanguage language, final String text) {
    return new AntlrLanguageFrontend().parse(new SourceUnit("sample", language, text));
  }

  @Test
  void swiftOperatorAtStartOfFileIsParsedWithoutThrowing() {
    assertThatCode(() -> parse(AnalysisLanguage.SWIFT, "+a\n")).doesNotThrowAnyException();
    assertThat(parse(AnalysisLanguage.SWIFT, "+a\n").tree()).isNotNull();
  }

  @Test
  void swiftOperatorAtEndOfFileIsParsedWithoutThrowing() {
    assertThatCode(() -> parse(AnalysisLanguage.SWIFT, "let a = 1 +")).doesNotThrowAnyException();
  }
}
