package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The inside filter of the forbidden-node check, with plain kinds and parent>child selectors. */
class ForbiddenNodeInsideTest {

  private final TreeLayout gen = new TreeLayout();

  private ForbiddenNodeCheck check(final Set<String> inside, final Set<String> notInside) {
    final NodeFilter filter =
        new NodeFilter(
            Set.of("decl"), ModifierTable.NONE, Set.of(), Set.of(), inside, notInside, Sources.ALL);
    return new ForbiddenNodeCheck(filter, ForbiddenNodeCheck.Condition.ANY, "found");
  }

  private SyntaxNode decl() {
    return gen.node("decl", gen.leaf("tok", "let"));
  }

  private SyntaxNode loop() {
    return gen.node("loop", gen.leaf("'for'", "for"), gen.node("body", decl()));
  }

  @Test
  void aSelectorInsideReportsOnlyNodesUnderANodeWithTheChild() {
    final SyntaxNode root = gen.node("unit", loop(), gen.node("block", decl()));

    assertThat(check(Set.of("loop>'for'"), Set.of()).matches(root)).hasSize(1);
  }

  @Test
  void aPlainKindInsideStillMatchesByKindOnly() {
    final SyntaxNode root = gen.node("unit", loop(), gen.node("block", decl()));

    assertThat(check(Set.of("loop"), Set.of()).matches(root)).hasSize(1);
    assertThat(check(Set.of("loop"), Set.of()).matches(gen.node("unit", decl()))).isEmpty();
  }

  @Test
  void aSelectorNotInsideExcludesNodesUnderANodeWithTheChild() {
    final SyntaxNode root = gen.node("unit", loop(), gen.node("block", decl()));

    assertThat(check(Set.of(), Set.of("loop>'while'")).matches(root)).hasSize(2);
    assertThat(check(Set.of(), Set.of("loop>'for'")).matches(root)).hasSize(1);
  }

  @Test
  void anEmptyInsideDoesNotLimit() {
    assertThat(check(Set.of(), Set.of()).matches(gen.node("unit", decl()))).hasSize(1);
  }
}
