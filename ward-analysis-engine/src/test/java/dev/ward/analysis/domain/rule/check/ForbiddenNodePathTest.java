package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The ancestor path condition of the forbidden-node check. */
class ForbiddenNodePathTest {

  private final TreeLayout gen = new TreeLayout();

  private ForbiddenNodeCheck check(final String path) {
    return new ForbiddenNodeCheck(
        NodeFilter.of(Set.of("decl")),
        new ForbiddenNodeCheck.Condition(Optional.empty(), Set.of(), Set.of(), path),
        "found");
  }

  private SyntaxNode decl() {
    return gen.node("decl", gen.leaf("tok", "let"));
  }

  private SyntaxNode clauseWith(final SyntaxNode child) {
    return gen.node("clause", gen.node("list", gen.node("stmt", child)));
  }

  @Test
  void aNodeIsReportedOnlyWhenItsNearestAncestorsFollowThePath() {
    final SyntaxNode root =
        gen.node(
            "unit",
            clauseWith(decl()),
            gen.node("clause", gen.node("list", gen.node("block", decl()))));

    assertThat(check("stmt>list>clause").matches(root)).hasSize(1);
  }

  @Test
  void aStepMayListAlternativeKinds() {
    final SyntaxNode root =
        gen.node(
            "unit",
            clauseWith(decl()),
            gen.node("other", gen.node("list", gen.node("stmt", decl()))));

    assertThat(check("stmt>list>clause|other").matches(root)).hasSize(2);
    assertThat(check("stmt>list>clause").matches(root)).hasSize(1);
  }

  @Test
  void aPathLongerThanTheAncestorsMatchesNothing() {
    assertThat(check("stmt>list>clause>unit>extra").matches(gen.node("unit", clauseWith(decl()))))
        .isEmpty();
  }

  @Test
  void anEmptyPathDoesNotLimit() {
    assertThat(check("").matches(gen.node("unit", clauseWith(decl())))).hasSize(1);
  }
}
