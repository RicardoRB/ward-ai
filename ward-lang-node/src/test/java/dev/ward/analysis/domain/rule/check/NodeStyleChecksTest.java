package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the TypeScript style checks on parsed sources; each finding is one match. */
class NodeStyleChecksTest {

  private static List<RuleMatch> run(final RuleCheck check, final String text) {
    return check.matches(
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("src/sample", AnalysisLanguage.TYPESCRIPT, text))
            .tree());
  }

  @Test
  void constructorAfterOtherMemberThatFollowedAConstructorIsReported() {
    assertThat(
            run(
                new NodeConstructorGroupCheck("grouped"),
                "class A { constructor() {} m() {} constructor(x) {} }"))
        .hasSize(1);
  }

  @Test
  void groupedConstructorsAreNotReported() {
    assertThat(
            run(
                new NodeConstructorGroupCheck("grouped"),
                "class A { constructor() {} constructor(x) {} m() {} }"))
        .isEmpty();
  }

  @Test
  void overloadAfterAnotherMemberIsReported() {
    assertThat(run(new NodeOverloadGroupingCheck("adjacent"), "class A { a() {} b() {} a() {} }"))
        .hasSize(1);
  }

  @Test
  void adjacentOverloadsAreNotReported() {
    assertThat(run(new NodeOverloadGroupingCheck("adjacent"), "class A { a() {} a() {} b() {} }"))
        .isEmpty();
  }

  @Test
  void elseAfterReturnIsReported() {
    assertThat(
            run(
                new NodeElseAfterJumpCheck(true, "else"),
                "function f(x) { if (x) { return 1; } else { return 2; } }"))
        .hasSize(1);
  }

  @Test
  void elseIfAfterReturnIsReportedOnlyWhenNotAllowed() {
    final String text = "function f(x) { if (x) { return 1; } else if (x > 1) { return 2; } }";
    assertThat(run(new NodeElseAfterJumpCheck(true, "else"), text)).isEmpty();
    assertThat(run(new NodeElseAfterJumpCheck(false, "else"), text)).hasSize(1);
  }

  @Test
  void elseAfterConsequentThatDoesNotReturnIsNotReported() {
    assertThat(
            run(
                new NodeElseAfterJumpCheck(true, "else"),
                "function f(x) { if (x) { x = 1; } else { x = 2; } }"))
        .isEmpty();
  }
}
