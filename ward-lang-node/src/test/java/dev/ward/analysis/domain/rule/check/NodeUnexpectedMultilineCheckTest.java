package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reports a call, bracket access or tagged template that starts on a later line. */
class NodeUnexpectedMultilineCheckTest {

  /** Scenario: a call whose parenthesis starts a line is reported at the parenthesis. */
  @Test
  void aCallWithItsParenthesisOnALaterLineIsReported() {
    final List<RuleMatch> found = run("const a = foo\n(1);\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startLine()).isEqualTo(2);
    assertThat(found.get(0).span().startColumn()).isEqualTo(1);
  }

  /** Scenario: a bracket access and a tagged template on a later line are reported. */
  @Test
  void bracketAndTemplateOnALaterLineAreReported() {
    assertThat(run("const b = c\n[0];\n")).hasSize(1);
    assertThat(run("const d = e\n`x`;\n")).hasSize(1);
  }

  /** Scenario: an expression on one line, and a member continued on the next, are clean. */
  @Test
  void sameLineCallAndLeadingDotAreClean() {
    assertThat(run("const a = foo(1);\nconst f = g\n  .h;\n")).isEmpty();
  }

  private static List<RuleMatch> run(final String source) {
    final SourceUnit unit = new SourceUnit("a", AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    return new NodeUnexpectedMultilineCheck().matches(root);
  }
}
