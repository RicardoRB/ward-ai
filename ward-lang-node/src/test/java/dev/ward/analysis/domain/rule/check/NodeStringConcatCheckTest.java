package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the string concatenation check on parsed JavaScript; each finding is one match. */
class NodeStringConcatCheckTest {

  @Test
  void chainWithStringAndVariableIsReportedOnce() {
    assertThat(run("const s = 'a' + b + 'c';")).hasSize(1);
  }

  @Test
  void chainOfStringLiteralsOnlyIsNotReported() {
    assertThat(run("const s = 'a' + 'b';")).isEmpty();
  }

  @Test
  void numberPlusVariableIsNotReported() {
    assertThat(run("const s = 1 + b;")).isEmpty();
  }

  @Test
  void parenthesizedOperandBelongsToTheSameChain() {
    assertThat(run("const s = 'a' + (b + 'c');")).hasSize(1);
  }

  @Test
  void stringAndNumberIsReported() {
    assertThat(run("const s = 'a' + 1;")).hasSize(1);
  }

  private static List<RuleMatch> run(final String source) {
    final SyntaxNode root =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("m", AnalysisLanguage.JAVASCRIPT, source))
            .tree();
    return new NodeStringConcatCheck("concat").matches(root);
  }
}
