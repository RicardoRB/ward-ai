package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reports a private member that is never read; a write alone does not use it. */
class NodeUnusedPrivateMembersCheckTest {

  /** Scenario: a member that is never mentioned is reported with its name. */
  @Test
  void aNeverUsedMemberIsReported() {
    final List<RuleMatch> found = run("class A {\n  #a = 1;\n  n() { return 2; }\n}\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("'#a' is defined but never used");
    assertThat(found.get(0).span().startLine()).isEqualTo(2);
  }

  /** Scenario: a member that is only assigned or incremented is reported. */
  @Test
  void aWriteOnlyMemberIsReported() {
    final List<RuleMatch> found =
        run("class A {\n  #b = 2;\n  #c = 3;\n  n() {\n    this.#b = 5;\n    this.#c++;\n  }\n}\n");

    assertThat(found).hasSize(2);
  }

  /** Scenario: a member that is read, by a method call or a getter, is not reported. */
  @Test
  void aReadMemberIsNotReported() {
    assertThat(run("class A {\n  #m() {}\n  n() { return this.#m; }\n}\n")).isEmpty();
  }

  private static List<RuleMatch> run(final String source) {
    final SourceUnit unit = new SourceUnit("a", AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    return new NodeUnusedPrivateMembersCheck().matches(root);
  }
}
