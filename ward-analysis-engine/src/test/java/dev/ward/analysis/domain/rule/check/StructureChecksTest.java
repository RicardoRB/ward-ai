package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class StructureChecksTest {

  private static final String BLOCK = "block";
  private static final String IF = "if";
  private static final String METHOD = "method";
  private static final String STMT = "stmt";
  private static final String SWITCH = "switch";
  private static final String UNIT = "unit";

  private final TreeLayout gen = new TreeLayout();

  @Test
  void forbiddenNodeReportsEachNodeOfTheKind() {
    final SyntaxNode root = gen.node(UNIT, gen.leaf("goto", "goto"), gen.leaf("goto", "goto"));
    final ForbiddenNodeCheck check =
        new ForbiddenNodeCheck(
            NodeFilter.of(Set.of("goto")), ForbiddenNodeCheck.Condition.ANY, "no {value}");

    assertThat(TreeLayout.messages(check.matches(root))).containsExactly("no goto", "no goto");
  }

  @Test
  void forbiddenNodeCanLimitByTextChildAndParent() {
    final SyntaxNode plain = gen.node("call", gen.leaf("id", "println"));
    final SyntaxNode clone = gen.node("call", gen.leaf("id", "clone"), gen.leaf("arg", "x"));
    final SyntaxNode root = gen.node(UNIT, gen.node(STMT, plain), gen.node("expr", clone));
    final NodeFilter calls = NodeFilter.of(Set.of("call"));

    assertThat(
            new ForbiddenNodeCheck(
                    calls,
                    new ForbiddenNodeCheck.Condition(
                        Optional.of(Pattern.compile("^clone")), Set.of(), Set.of()),
                    "m")
                .matches(root))
        .hasSize(1);
    assertThat(
            new ForbiddenNodeCheck(
                    calls,
                    new ForbiddenNodeCheck.Condition(Optional.empty(), Set.of("arg"), Set.of()),
                    "m")
                .matches(root))
        .hasSize(1);
    assertThat(
            new ForbiddenNodeCheck(
                    calls,
                    new ForbiddenNodeCheck.Condition(Optional.empty(), Set.of(), Set.of(STMT)),
                    "m")
                .matches(root))
        .hasSize(1);
  }

  private RequiredNodeCheck switchNeedsDefault() {
    return new RequiredNodeCheck(
        NodeFilter.of(Set.of("stmt>switch")),
        new RequiredNodeCheck.Requirement(Set.of(), Set.of("default"), Set.of(BLOCK), Set.of()),
        "missing default");
  }

  @Test
  void requiredNodeReportsASwitchWithoutDefault() {
    final SyntaxNode with =
        gen.node(STMT, gen.leaf(SWITCH, SWITCH), gen.node("group", gen.leaf("default", "default")));
    final SyntaxNode without = gen.node(STMT, gen.leaf(SWITCH, SWITCH), gen.leaf("case", "case"));
    final SyntaxNode other = gen.node(STMT, gen.leaf("if", IF));
    final SyntaxNode root = gen.node(UNIT, with, without, other);

    assertThat(TreeLayout.starts(switchNeedsDefault().matches(root)))
        .containsExactly(without.span().startOffset());
  }

  @Test
  void aKindCanNameAChildTokenAndAQuotedGreaterThan() {
    final SyntaxNode plus = gen.node("expr", gen.leaf("'+'", "+"));
    final SyntaxNode gt = gen.node("expr", gen.leaf("'>'", ">"));
    final SyntaxNode root = gen.node(UNIT, plus, gt);

    assertThat(
            new ForbiddenNodeCheck(
                    NodeFilter.of(Set.of("expr>'>'")), ForbiddenNodeCheck.Condition.ANY, "m")
                .matches(root))
        .hasSize(1);
  }

  @Test
  void requiredNodeDoesNotCountADefaultOfANestedSwitch() {
    final SyntaxNode open = gen.leaf(SWITCH, SWITCH);
    final SyntaxNode nested =
        gen.node(BLOCK, gen.node(STMT, gen.leaf(SWITCH, SWITCH), gen.leaf("default", "default")));
    final SyntaxNode outer = gen.node(STMT, open, nested);

    assertThat(TreeLayout.starts(switchNeedsDefault().matches(gen.node(UNIT, outer))))
        .containsExactly(outer.span().startOffset());
  }

  @Test
  void requiredNodeChecksChildAndSibling() {
    final SyntaxNode lone = gen.node("if", gen.leaf("cond", "c"));
    final SyntaxNode paired =
        gen.node("if", gen.leaf("cond", "c"), gen.node(BLOCK, gen.leaf("b", "b")));
    final SyntaxNode root = gen.node(UNIT, lone, paired);
    final NodeFilter ifs = NodeFilter.of(Set.of("if"));

    assertThat(
            new RequiredNodeCheck(
                    ifs,
                    new RequiredNodeCheck.Requirement(Set.of(BLOCK), Set.of(), Set.of(), Set.of()),
                    "braces")
                .matches(root))
        .hasSize(1);
    assertThat(
            new RequiredNodeCheck(
                    ifs,
                    new RequiredNodeCheck.Requirement(Set.of(), Set.of(), Set.of(), Set.of("if")),
                    "sibling")
                .matches(root))
        .isEmpty();
    assertThatThrownBy(
            () -> new RequiredNodeCheck.Requirement(Set.of(), Set.of(), Set.of(), Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void countReportsAScopeAboveTheLimitAndSkipsNestedScopes() {
    final SyntaxNode one = gen.leaf("ret", "return");
    final SyntaxNode two = gen.leaf("ret", "return");
    final SyntaxNode lambda = gen.node("lambda", gen.leaf("ret", "return"));
    final SyntaxNode method = gen.node(METHOD, one, two, lambda);
    final SyntaxNode small = gen.node(METHOD, gen.leaf("ret", "return"));
    final CountCheck check =
        new CountCheck(
            NodeFilter.of(Set.of(METHOD)), Set.of("ret"), Set.of("lambda"), 1, "{value} > {max}");

    assertThat(TreeLayout.messages(check.matches(gen.node(UNIT, method, small))))
        .containsExactly("2 > 1");
    assertThat(
            new CountCheck(NodeFilter.of(Set.of(METHOD)), Set.of("ret"), Set.of(), 1, "m")
                .matches(gen.node(UNIT, method)))
        .hasSize(1);
    assertThatThrownBy(() -> new CountCheck(NodeFilter.of(Set.of()), Set.of(), Set.of(), -1, "m"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void depthReportsTheInnermostIfBeyondTheLimit() {
    final SyntaxNode third = gen.node(IF, gen.leaf("c", "c3"));
    final SyntaxNode second = gen.node(IF, gen.node(BLOCK, third));
    final SyntaxNode first = gen.node(IF, gen.node(BLOCK, second));
    final DepthCheck check =
        new DepthCheck(NodeFilter.of(Set.of(IF)), Set.of(METHOD), 1, "depth {value} > {max}");

    assertThat(TreeLayout.messages(check.matches(gen.node(UNIT, gen.node(METHOD, first)))))
        .containsExactly("depth 2 > 1");
    assertThat(TreeLayout.starts(check.matches(gen.node(UNIT, first))))
        .containsExactly(third.span().startOffset());
  }

  @Test
  void depthRestartsAtAResetKind() {
    final SyntaxNode inner = gen.node(IF, gen.leaf("c", "c2"));
    final SyntaxNode method = gen.node(METHOD, gen.node(IF, gen.node(BLOCK, inner)));
    final SyntaxNode outer = gen.node(IF, gen.node(BLOCK, method));

    assertThat(
            new DepthCheck(NodeFilter.of(Set.of(IF)), Set.of(METHOD), 0, "m")
                .matches(gen.node(UNIT, outer)))
        .hasSize(1);
  }
}
