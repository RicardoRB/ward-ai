package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reports an else-if condition that an earlier condition of the chain makes unreachable. */
class NodeDupeElseIfCheckTest {

  private static final int LINE = 3;
  private static final int COLUMN = 12;
  private static final String MESSAGE = "unreachable branch";

  /** Scenario: a repeated condition is reported at the repeated test. */
  @Test
  void aRepeatedConditionIsReported() {
    final List<RuleMatch> found = run("if (a) {\n} else if (b) {\n} else if (b) {\n}\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startLine()).isEqualTo(LINE);
    assertThat(found.get(0).span().startColumn()).isEqualTo(COLUMN);
  }

  /** Scenario: a conjunction whose operand failed before is covered, as ESLint reports it. */
  @Test
  void aConjunctionWithAFailedOperandIsCovered() {
    assertThat(run("if (a) {\n} else if (a && c) {\n}\n")).hasSize(1);
  }

  /** Scenario: a disjunction is covered only when both of its atoms are known false. */
  @Test
  void aDisjunctionWithOneKnownAtomIsNotCovered() {
    assertThat(run("if (a) {\n} else if (a || d) {\n}\n")).isEmpty();
  }

  /** Scenario: an earlier disjunction makes its atoms false for the later branches. */
  @Test
  void anEarlierDisjunctionCoversItsAtoms() {
    assertThat(run("if (a) {\n} else if (e || f) {\n} else if (e) {\n}\n")).hasSize(1);
  }

  private static List<RuleMatch> run(final String source) {
    final SourceUnit unit = new SourceUnit("a", AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    return new NodeDupeElseIfCheck(MESSAGE).matches(root);
  }
}
