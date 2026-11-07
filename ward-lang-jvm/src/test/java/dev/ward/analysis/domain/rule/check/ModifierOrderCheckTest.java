package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.n;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.t;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModifierOrderCheckTest {

  private static final String MODIFIER = "modifier";
  private static final String MESSAGE = "message";
  private static final String BODY = "classBodyDeclaration";

  @Test
  void reportsAKeywordThatComesAfterALowerRankedOne() {
    assertThat(run(n(BODY, modifier("static"), modifier("public")))).hasSize(1);
    assertThat(run(n(BODY, modifier("public"), modifier("static"), modifier("final")))).isEmpty();
  }

  @Test
  void reportsAnAnnotationThatFollowsAKeyword() {
    assertThat(run(n(BODY, modifier("public"), modifier("@Override")))).hasSize(1);
    assertThat(run(n(BODY, modifier("@Override"), modifier("public")))).isEmpty();
  }

  @Test
  void ignoresUnknownWordsAndNonModifierChildren() {
    final SyntaxNode holder =
        n(BODY, modifier("unknown"), t("identifier", "public"), modifier("public"));
    assertThat(run(holder)).isEmpty();
  }

  @Test
  void checksEachHolderBelowTheRoot() {
    assertThat(run(n(BODY, n("memberDeclaration", modifier("final"), modifier("public")))))
        .hasSize(1);
  }

  private static List<RuleMatch> run(final SyntaxNode root) {
    return unitMatches(new ModifierOrderCheck(MESSAGE), root);
  }

  private static SyntaxNode modifier(final String text) {
    return n(MODIFIER, t(MODIFIER, text));
  }
}
