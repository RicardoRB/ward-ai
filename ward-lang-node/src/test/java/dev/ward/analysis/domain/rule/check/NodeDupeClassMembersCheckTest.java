package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reports a class member that repeats an earlier name, except a getter and setter pair. */
class NodeDupeClassMembersCheckTest {

  private static final int LINE = 3;
  private static final int COLUMN = 3;
  private static final String MESSAGE = "duplicate name '{value}'";

  /** Scenario: a repeated method is reported at the later name. */
  @Test
  void aRepeatedMethodIsReportedAtTheLaterName() {
    final List<RuleMatch> found = run("class A {\n  foo() {}\n  foo() {}\n}\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startLine()).isEqualTo(LINE);
    assertThat(found.get(0).span().startColumn()).isEqualTo(COLUMN);
    assertThat(found.get(0).message()).isEqualTo("duplicate name 'foo'");
  }

  /** Scenario: a getter and a setter of one name are a valid pair. */
  @Test
  void aGetterAndSetterPairIsValid() {
    assertThat(run("class A {\n  get x() { return 1; }\n  set x(v) {}\n}\n")).isEmpty();
  }

  /** Scenario: a static member does not repeat a non-static one of the same name. */
  @Test
  void staticAndInstanceMembersDoNotRepeat() {
    assertThat(run("class A {\n  foo() {}\n  static foo() {}\n}\n")).isEmpty();
  }

  private static List<RuleMatch> run(final String source) {
    final SourceUnit unit = new SourceUnit("a", AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    return new NodeDupeClassMembersCheck(MESSAGE).matches(root);
  }
}
