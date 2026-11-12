package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A {@code TEXT} rule runs over files with no frontend and no language. */
final class RuleEngineTextTest {

  private static final String BAD = "bad.txt";

  @Test
  void textRuleRunsWithoutAFrontendAndFindingsComeBackInPathOrder() {
    final RuleRunResult result =
        new RuleEngine(Seen::new)
            .runText(
                List.of(new TextFile("b.txt", "x"), new TextFile("a.txt", "x")),
                List.of(rule("ward:secrets/one")));

    assertThat(result.findings()).extracting("file").containsExactly("a.txt", "b.txt");
    assertThat(result.notRun()).isEmpty();
    assertThat(result.executionErrors()).isEmpty();
  }

  @Test
  void aRuleThatFailsOnAFileIsAnExecutionErrorAndTheRunContinues() {
    final RuleRunResult result =
        new RuleEngine(Seen::new)
            .runText(
                List.of(new TextFile(BAD, "x"), new TextFile("good.txt", "x")),
                List.of(rule("ward:secrets/one")));

    assertThat(result.findings()).extracting("file").containsExactly("good.txt");
    assertThat(result.executionErrors()).hasSize(1);
    assertThat(result.executionErrors().get(0).path()).isEqualTo(BAD);
    assertThat(result.executionErrors().get(0).ruleId()).isEqualTo("ward:secrets/one");
    assertThat(result.executionErrors().get(0).reason()).isEqualTo("over budget");
  }

  @Test
  void aRuleThatCannotBeCompiledIsAnExecutionErrorWithNoPath() {
    final RuleRunResult result =
        new RuleEngine(
                definition -> {
                  throw new AnalysisFailedException("bad");
                })
            .runText(List.of(new TextFile("a.txt", "x")), List.of(rule("ward:secrets/one")));

    assertThat(result.executionErrors()).hasSize(1);
    assertThat(result.executionErrors().get(0).path()).isNull();
  }

  private static RuleDefinition rule(final String id) {
    return new RuleDefinition(id, Set.of(), RuleCapability.TEXT, AnalysisSeverity.ERROR, "body");
  }

  private record Seen(RuleDefinition definition) implements CompiledRule {
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
