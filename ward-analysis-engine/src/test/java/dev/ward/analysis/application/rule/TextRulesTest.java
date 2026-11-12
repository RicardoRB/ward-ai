package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.domain.rule.TextCompiledRule;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A {@code TEXT} rule runs over files with no frontend and no language. */
final class TextRulesTest {

  private static final String BAD = "bad.txt";

  @Test
  void textRuleRunsWithoutAFrontendAndFindingsComeBackInPathOrder() {
    final RuleRunResult result =
        new TextRules(Seen::new, List.of(rule("ward:secrets/one")))
            .run(List.of(new TextFile("b.txt", "x"), new TextFile("a.txt", "x")));

    assertThat(result.findings()).extracting("file").containsExactly("a.txt", "b.txt");
    assertThat(result.notRun()).isEmpty();
    assertThat(result.executionErrors()).isEmpty();
  }

  @Test
  void aRuleThatFailsOnAFileIsAnExecutionErrorAndTheRunContinues() {
    final RuleRunResult result =
        new TextRules(Seen::new, List.of(rule("ward:secrets/one")))
            .run(List.of(new TextFile(BAD, "x"), new TextFile("good.txt", "x")));

    assertThat(result.findings()).extracting("file").containsExactly("good.txt");
    assertThat(result.executionErrors()).hasSize(1);
    assertThat(result.executionErrors().get(0).path()).isEqualTo(BAD);
    assertThat(result.executionErrors().get(0).ruleId()).isEqualTo("ward:secrets/one");
    assertThat(result.executionErrors().get(0).reason()).isEqualTo("over budget");
  }

  @Test
  void aRuleThatCannotBeCompiledIsAnExecutionErrorWithNoPath() {
    final RuleRunResult result =
        new TextRules(
                definition -> {
                  throw new AnalysisFailedException("bad");
                },
                List.of(rule("ward:secrets/one")))
            .run(List.of(new TextFile("a.txt", "x")));

    assertThat(result.executionErrors()).hasSize(1);
    assertThat(result.executionErrors().get(0).path()).isNull();
  }

  private static RuleDefinition rule(final String id) {
    return new RuleDefinition(id, Set.of(), RuleCapability.TEXT, AnalysisSeverity.ERROR, "body");
  }

  private record Seen(RuleDefinition definition) implements TextCompiledRule {
    @Override
    public List<RuleMatch> matches(final dev.ward.analysis.domain.ir.SyntaxNode root) {
      return List.of();
    }

    @Override
    public List<RuleMatch> textMatches(final TextFile file) {
      if (BAD.equals(file.path())) {
        throw new AnalysisFailedException("over budget");
      }
      return List.of(new RuleMatch(new Span(1, 1, 1, 2, 0, 1), "seen"));
    }
  }
}
