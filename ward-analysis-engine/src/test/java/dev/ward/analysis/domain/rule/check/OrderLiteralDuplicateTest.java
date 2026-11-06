package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderLiteralDuplicateTest {

  private static final String NUMBER = "number";
  private static final String UNIT = "unit";
  private static final String IMPORT = "import";

  private final TreeLayout gen = new TreeLayout();

  @Test
  void orderReportsAChildThatComesAfterALaterRankedOne() {
    final SyntaxNode root =
        gen.node(
            UNIT,
            gen.node(
                "mods", gen.leaf("m", "static"), gen.leaf("m", "public"), gen.leaf("m", "final")));
    final OrderCheck check =
        new OrderCheck(
            NodeFilter.of(Set.of("mods")),
            List.of("public", "protected", "private", "abstract", "static", "final"),
            "{value} is out of order");

    assertThat(TreeLayout.messages(check.matches(root))).containsExactly("public is out of order");
  }

  @Test
  void orderRanksByKindOrSelectorAndIgnoresUnrankedChildren() {
    final SyntaxNode root =
        gen.node(
            UNIT,
            gen.node(
                "body",
                gen.leaf("method", "m"),
                gen.leaf("other", "o"),
                gen.node("wrap", gen.leaf("field", "f"))));
    final NodeFilter body = NodeFilter.of(Set.of("body"));

    assertThat(new OrderCheck(body, List.of("wrap>field", "method"), "late").matches(root))
        .hasSize(1);
    assertThat(new OrderCheck(body, List.of("method", "wrap>field"), "late").matches(root))
        .isEmpty();
  }

  private LiteralCheck magic(final int repeats) {
    return new LiteralCheck(
        NodeFilter.of(Set.of(NUMBER)),
        Set.of("0", "1"),
        Set.of("constant"),
        repeats,
        "magic {value}");
  }

  @Test
  void literalReportsValuesOutsideTheAllowedOnesAndContexts() {
    final SyntaxNode root =
        gen.node(
            UNIT,
            gen.node("expr", gen.leaf(NUMBER, "60")),
            gen.node("constant", gen.leaf(NUMBER, "24")),
            gen.leaf(NUMBER, "1"));

    assertThat(TreeLayout.messages(magic(LiteralCheck.NO_REPEAT_LIMIT).matches(root)))
        .containsExactly("magic 60");
  }

  @Test
  void literalWithARepeatLimitReportsOnlyTheRepeats() {
    final SyntaxNode root =
        gen.node(
            UNIT,
            gen.leaf(NUMBER, "7"),
            gen.leaf(NUMBER, "7"),
            gen.leaf(NUMBER, "7"),
            gen.leaf(NUMBER, "8"));

    assertThat(magic(1).matches(root)).hasSize(2);
  }

  @Test
  void duplicateReportsALaterSiblingWithTheSameKey() {
    final SyntaxNode first = gen.node(IMPORT, gen.leaf("name", "a.B"));
    final SyntaxNode other = gen.node(IMPORT, gen.leaf("name", "a.C"));
    final SyntaxNode again = gen.node(IMPORT, gen.leaf("name", "a.B"));
    final SyntaxNode root = gen.node(UNIT, first, other, again);
    final NodeFilter parent = NodeFilter.of(Set.of(UNIT));

    final DuplicateCheck check =
        new DuplicateCheck(parent, Set.of(IMPORT), Optional.of("name"), "dup {value}");

    assertThat(TreeLayout.messages(check.matches(root))).containsExactly("dup a.B");
    assertThat(TreeLayout.starts(check.matches(root))).containsExactly(again.span().startOffset());
    assertThat(new DuplicateCheck(parent, Set.of(IMPORT), Optional.empty(), "d").matches(root))
        .hasSize(1);
    assertThat(new DuplicateCheck(parent, Set.of(IMPORT), Optional.of("absent"), "d").matches(root))
        .isEmpty();
  }
}
