package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The unused-label check on parsed TypeScript and JavaScript. */
class UnusedLabelCheckTest {

  private static List<RuleMatch> run(final AnalysisLanguage language, final String source) {
    final SourceUnit unit = new SourceUnit("m", language, source);
    return new UnusedLabelCheck("unused label '{value}'")
        .matches(new AntlrLanguageFrontend().parse(unit).tree());
  }

  @Test
  void aLabelNoJumpNamesIsReported() {
    for (final AnalysisLanguage language :
        List.of(AnalysisLanguage.TYPESCRIPT, AnalysisLanguage.JAVASCRIPT)) {
      assertThat(run(language, "outer: for (;;) { break; }\n"))
          .extracting(RuleMatch::message)
          .containsExactly("unused label 'outer'");
    }
  }

  @Test
  void aLabelNamedByBreakOrContinueIsUsed() {
    for (final AnalysisLanguage language :
        List.of(AnalysisLanguage.TYPESCRIPT, AnalysisLanguage.JAVASCRIPT)) {
      assertThat(run(language, "a: for (;;) { for (;;) { break a; } }\n")).isEmpty();
      assertThat(run(language, "b: for (;;) { continue b; }\n")).isEmpty();
    }
  }

  @Test
  void eachLabelIsJudgedOnItsOwn() {
    final List<RuleMatch> found =
        run(AnalysisLanguage.JAVASCRIPT, "a: for (;;) { b: for (;;) { break a; } }\n");

    assertThat(found).extracting(RuleMatch::message).containsExactly("unused label 'b'");
  }
}
