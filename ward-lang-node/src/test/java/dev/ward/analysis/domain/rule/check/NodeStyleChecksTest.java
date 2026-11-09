package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the TypeScript style checks on parsed sources; each finding is one match. */
class NodeStyleChecksTest {

  private static SyntaxNode parse(final String text) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit("src/sample", AnalysisLanguage.TYPESCRIPT, text))
        .tree();
  }

  private static List<RuleMatch> constructorGroup(final String text) {
    return new NodeConstructorGroupCheck("grouped").matches(parse(text));
  }

  private static List<RuleMatch> overloadGroup(final String text) {
    return new NodeOverloadGroupingCheck("adjacent").matches(parse(text));
  }

  private static List<RuleMatch> elseAfterJump(final String text, final boolean allowElseIf) {
    return new NodeElseAfterJumpCheck(allowElseIf, "else").matches(parse(text));
  }

  @Test
  void constructorAfterOtherMemberThatFollowedAConstructorIsReported() {
    assertThat(constructorGroup("class A { constructor() {} m() {} constructor(x) {} }")).hasSize(1);
  }

  @Test
  void groupedConstructorsAreNotReported() {
    assertThat(constructorGroup("class A { constructor() {} constructor(x) {} m() {} }")).isEmpty();
  }

  @Test
  void overloadAfterAnotherMemberIsReported() {
    assertThat(overloadGroup("class A { a() {} b() {} a() {} }")).hasSize(1);
  }

  @Test
  void adjacentOverloadsAreNotReported() {
    assertThat(overloadGroup("class A { a() {} a() {} b() {} }")).isEmpty();
  }

  @Test
  void elseAfterReturnIsReported() {
    assertThat(elseAfterJump("function f(x) { if (x) { return 1; } else { return 2; } }", true))
        .hasSize(1);
  }

  @Test
  void elseIfAfterReturnIsReportedOnlyWhenNotAllowed() {
    final String text = "function f(x) { if (x) { return 1; } else if (x > 1) { return 2; } }";
    assertThat(elseAfterJump(text, true)).isEmpty();
    assertThat(elseAfterJump(text, false)).hasSize(1);
  }

  @Test
  void elseAfterConsequentThatDoesNotReturnIsNotReported() {
    assertThat(elseAfterJump("function f(x) { if (x) { x = 1; } else { x = 2; } }", true))
        .isEmpty();
  }
}
