package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The ancestor check's placement modes and the str.format check's argument handling. */
final class AncestorAndDotChecksTest {

  @Test
  void ancestorOutsideReportsABreakNotInsideALoop() {
    final AncestorCheck outside = outside("break_stmt", "for_stmt");

    assertThat(
            outside.matches(
                Nodes.node("file_input", Nodes.node("break_stmt", Nodes.leaf("break")))))
        .hasSize(1);
    assertThat(
            outside.matches(Nodes.node("file_input", Nodes.node("for_stmt", Nodes.leaf("break")))))
        .isEmpty();
  }

  @Test
  void ancestorInsideReportsAYieldFromInsideAnAsyncFunction() {
    final AncestorCheck inside = inside("yield_expr", "async_funcdef");
    final SyntaxNode asyncYield =
        Nodes.node(
            "async_funcdef", Nodes.node("funcdef", Nodes.node("yield_expr", Nodes.leaf("yield"))));
    final SyntaxNode plainYield =
        Nodes.node("funcdef", Nodes.node("yield_expr", Nodes.leaf("yield")));

    assertThat(inside.matches(asyncYield)).hasSize(1);
    assertThat(inside.matches(plainYield)).isEmpty();
  }

  @Test
  void ancestorTextPatternSelectsOnlyMatchingNodes() {
    final AncestorCheck awaits =
        new AncestorCheck(
            Set.of("atom_expr"),
            new AncestorCheck.Boundary(Set.of("async_funcdef"), Set.of("funcdef")),
            AncestorCheck.Mode.OUTSIDE,
            Optional.of(Pattern.compile("^await")),
            "await outside async");
    final SyntaxNode awaiting = Nodes.node("funcdef", Nodes.node("atom_expr", Nodes.leaf("await")));
    final SyntaxNode notAwait = Nodes.node("funcdef", Nodes.node("atom_expr", Nodes.leaf("h")));

    assertThat(awaits.matches(awaiting)).hasSize(1);
    assertThat(awaits.matches(notAwait)).isEmpty();
  }

  @Test
  void dotFormatReportsUnusedNamedAndMissingFieldsAndAcceptsAMatchingCall() {
    final DotFormatCheck unusedNamed = new DotFormatCheck(DotFormatCheck.Problem.UNUSED_NAMED, "m");
    final DotFormatCheck missing = new DotFormatCheck(DotFormatCheck.Problem.MISSING, "m");

    assertThat(unusedNamed.matches(call("\"{x}\".format(x=1,y=2)"))).hasSize(1);
    assertThat(missing.matches(call("\"{x}\".format()"))).hasSize(1);
    assertThat(unusedNamed.matches(call("\"{x}\".format(x=1)"))).isEmpty();
  }

  @Test
  void dotFormatReadsSpecsAndIgnoresCallsItCannotJudge() {
    final DotFormatCheck badType = new DotFormatCheck(DotFormatCheck.Problem.BAD_CHARACTER, "m");

    assertThat(badType.matches(call("\"{:y}\".format(1)"))).hasSize(1);
    assertThat(badType.matches(call("\"{:d}\".format(1)"))).isEmpty();
    assertThat(badType.matches(call("\"{}\".format(*a)"))).isEmpty();
    assertThat(badType.matches(call("name.format(1)"))).isEmpty();
  }

  private static SyntaxNode call(final String text) {
    return Nodes.node("atom_expr", Nodes.leaf(text));
  }

  private static AncestorCheck outside(final String kind, final String allowed) {
    return new AncestorCheck(
        Set.of(kind),
        new AncestorCheck.Boundary(Set.of(allowed), Set.of("funcdef")),
        AncestorCheck.Mode.OUTSIDE,
        Optional.empty(),
        "outside");
  }

  private static AncestorCheck inside(final String kind, final String allowed) {
    return new AncestorCheck(
        Set.of(kind),
        new AncestorCheck.Boundary(Set.of(allowed), Set.of("funcdef")),
        AncestorCheck.Mode.INSIDE,
        Optional.empty(),
        "inside");
  }
}
