package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.util.List;
import org.junit.jupiter.api.Test;

class LanguageRuleMatcherTest {

  private static final Span ANY = new Span(1, 1, 1, 1, 0, 0);

  @Test
  void typeScriptAndJavaScriptRulesGoToTheStructuralMatcher() {
    final RuleMatcher matcher = new LanguageRuleMatcher(tagged("structural"), tagged("shared"));

    assertThat(messageOf(matcher, rule("ward:typescript/naming", AnalysisLanguage.TYPESCRIPT)))
        .isEqualTo("structural");
    assertThat(messageOf(matcher, rule("ward:javascript/naming", AnalysisLanguage.JAVASCRIPT)))
        .isEqualTo("structural");
  }

  @Test
  void otherLanguagesGoToTheSharedMatcher() {
    final RuleMatcher matcher = new LanguageRuleMatcher(tagged("structural"), tagged("shared"));

    assertThat(messageOf(matcher, rule("ward:python/function-length", AnalysisLanguage.PYTHON)))
        .isEqualTo("shared");
  }

  private static String messageOf(final RuleMatcher matcher, final RuleDefinition definition) {
    return matcher.compile(definition).matches(null).get(0).message();
  }

  private static RuleDefinition rule(final String id, final AnalysisLanguage language) {
    return new RuleDefinition(id, language, RuleCapability.SYNTAX, AnalysisSeverity.ERROR, "");
  }

  /** A matcher whose rules report the name of the matcher that built them. */
  private static RuleMatcher tagged(final String name) {
    return definition ->
        new CompiledRule() {
          @Override
          public RuleDefinition definition() {
            return definition;
          }

          @Override
          public List<RuleMatch> matches(final SyntaxNode root) {
            return List.of(new RuleMatch(ANY, name));
          }
        };
  }
}
