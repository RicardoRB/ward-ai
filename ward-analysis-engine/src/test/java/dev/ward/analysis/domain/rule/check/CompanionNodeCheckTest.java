package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CompanionNodeCheckTest {

  private static final String MATCH = "^add\\((\\w+)\\)$";
  private static final String COMPANION = "^{1}\\.fix\\(\\)$";

  private final TreeLayout gen = new TreeLayout();

  private CompanionNodeCheck check() {
    return new CompanionNodeCheck(
        NodeFilter.of(Set.of("method")),
        "expression",
        Pattern.compile(MATCH),
        COMPANION,
        "missing");
  }

  @Test
  void reportsANodeWhoseCompanionIsMissingFromItsBody() {
    final SyntaxNode root = gen.node("unit", gen.node("method", gen.leaf("expression", "add(c)")));

    assertThat(TreeLayout.messages(check().matches(root))).containsExactly("missing");
  }

  @Test
  void acceptsABodyWithTheCompanionOfTheCapturedName() {
    final SyntaxNode root =
        gen.node(
            "unit",
            gen.node(
                "method", gen.leaf("expression", "add(c)"), gen.leaf("expression", "c.fix()")));

    assertThat(check().matches(root)).isEmpty();
  }

  @Test
  void aCompanionOfAnotherNameDoesNotCount() {
    final SyntaxNode root =
        gen.node(
            "unit",
            gen.node(
                "method", gen.leaf("expression", "add(c)"), gen.leaf("expression", "d.fix()")));

    assertThat(check().matches(root)).hasSize(1);
  }

  @Test
  void aCompanionInAnotherBodyDoesNotCount() {
    final SyntaxNode root =
        gen.node(
            "unit",
            gen.node("method", gen.leaf("expression", "add(c)")),
            gen.node("method", gen.leaf("expression", "c.fix()")));

    assertThat(check().matches(root)).hasSize(1);
  }
}
