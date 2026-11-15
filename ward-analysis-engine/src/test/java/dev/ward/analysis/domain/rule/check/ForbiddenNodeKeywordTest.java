package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** A text pattern that starts with a keyword needs that keyword as the node's first token. */
class ForbiddenNodeKeywordTest {
  private static final String EXPRESSION = "expression";
  private static final String TOKEN = "tok";

  private final TreeLayout gen = new TreeLayout();

  private ForbiddenNodeCheck check(final String text) {
    return new ForbiddenNodeCheck(
        NodeFilter.of(Set.of(EXPRESSION)),
        new ForbiddenNodeCheck.Condition(
            Optional.of(Pattern.compile(text)),
            Set.of(),
            Set.of(),
            ""
        ),
        "found"
    );
  }

  private SyntaxNode expression(final String... tokens) {
    final SyntaxNode[] leaves = new SyntaxNode[tokens.length];
    for (int index = 0; index < tokens.length; index++) {
      leaves[index] = gen.leaf(TOKEN, tokens[index]);
    }
    return gen.node("unit", gen.node(EXPRESSION, leaves));
  }

  @Test
  void theKeywordFollowedByANameIsReported() {
    assertThat(check("^new(java\\.lang\\.)?Boolean\\(").matches(expression("new", "Boolean", "(")))
        .hasSize(1);
  }

  @Test
  void aCallToAMethodNamedLikeTheKeywordAndTheNameIsNotReported() {
    assertThat(check("^new(java\\.lang\\.)?Boolean\\(").matches(expression("newBoolean", "(")))
        .isEmpty();
  }

  @Test
  void everyTopLevelAlternativeMustStartWithAKeywordForTheTokenToCount() {
    final String keywords = "^new(a|b)\\|x\\(|^if\\(";
    assertThat(check(keywords).matches(expression("if", "("))).hasSize(1);
    assertThat(check(keywords).matches(expression("iffy", "("))).isEmpty();

    final String mixed = "^new[A-Z]|^[a-z]+y\\(";
    assertThat(check(mixed).matches(expression("iffy", "("))).hasSize(1);
  }
}
