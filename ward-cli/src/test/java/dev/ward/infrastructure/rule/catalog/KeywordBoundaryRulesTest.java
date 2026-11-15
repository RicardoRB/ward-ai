package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/**
 * A {@code forbidden-node} rule matches the text of a node without whitespace, so a call to a
 * method named like a keyword, such as {@code newBoolean(...)}, must not be read as the keyword
 * followed by the rest of the name.
 */
class KeywordBoundaryRulesTest {

  /** Fixed in another change of #347; remove it from here when that change lands. */
  private static final String OWNED_ELSEWHERE = "ward:java/control-statement-braces";

  private static final String CALLS =
      """
      final class Calls {
        boolean run(Helper helper) {
          final boolean a = newBoolean(true);
          newString("a");
          newThread(null);
          newURLClassLoader(null);
          newInteger(1);
          iffy(1);
          format("x");
          doWork();
          tryLock();
          whileAway();
          returnValue();
          throwIfInvalid(1);
          forEach(1);
          switchTo(1);
          synchronizedCall();
          assertThat(1);
          catchAll();
          elseValue();
          thisOrThat();
          superValue();
          helper.newBoolean(true);
          helper.iffy(1);
          helper.tryLock();
          return returnValue();
        }
      }
      """;

  @Test
  void noForbiddenNodeRuleReadsACallToAMethodNamedLikeAKeywordAsTheKeyword() {
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.all().stream()
            .filter(rule -> rule.languages().contains(AnalysisLanguage.JAVA))
            .filter(rule -> "forbidden-node".equals(Toml.parse(rule.body()).getString("check")))
            .toList();
    final RuleFixtureRunner runner = new RuleFixtureRunner();
    final TreeSet<String> reporting = new TreeSet<>();
    for (final RuleDefinition rule : rules) {
      final List<AnalysisFinding> findings =
          runner.run(rule, "Calls.java", CALLS, FixtureTypeFacts.of(java.util.Optional.empty()));
      if (findings.stream().anyMatch(finding -> finding.ruleId().equals(rule.id()))) {
        reporting.add(rule.id());
      }
    }

    assertThat(rules).hasSizeGreaterThan(100);
    reporting.remove(OWNED_ELSEWHERE);
    assertThat(reporting).isEmpty();
  }
}
