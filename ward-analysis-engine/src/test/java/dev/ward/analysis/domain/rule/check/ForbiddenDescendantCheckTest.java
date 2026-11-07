package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The forbidden-descendant check on small hand-built trees. */
class ForbiddenDescendantCheckTest {

  private final TreeLayout gen = new TreeLayout();

  private ForbiddenDescendantCheck check(final Optional<Pattern> text) {
    return new ForbiddenDescendantCheck(
        NodeFilter.of(Set.of("setter")),
        Set.of("return"),
        Set.of("function"),
        text,
        "found {value}");
  }

  private SyntaxNode returning(final String value) {
    return gen.node("return", gen.leaf("tok", "return"), gen.leaf("tok", value));
  }

  @Test
  void everyMatchingDescendantIsReportedWithItsText() {
    final SyntaxNode root =
        gen.node("unit", gen.node("setter", gen.node("body", returning("1"), returning("2"))));

    assertThat(TreeLayout.messages(check(Optional.empty()).matches(root)))
        .containsExactly("found return1", "found return2");
  }

  @Test
  void aNestedFunctionIsNotSearched() {
    final SyntaxNode root =
        gen.node("unit", gen.node("setter", gen.node("function", returning("1"))));

    assertThat(check(Optional.empty()).matches(root)).isEmpty();
  }

  @Test
  void theTextPatternLimitsTheMatches() {
    final SyntaxNode root =
        gen.node(
            "unit",
            gen.node("setter", gen.node("return", gen.leaf("tok", "return")), returning("2")));

    assertThat(TreeLayout.messages(check(Optional.of(Pattern.compile("^return.+"))).matches(root)))
        .containsExactly("found return2");
  }

  @Test
  void nodesOutsideTheFilterAreNotSearched() {
    assertThat(check(Optional.empty()).matches(gen.node("unit", gen.node("other", returning("1")))))
        .isEmpty();
  }
}
