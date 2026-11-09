package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The positional parameter count: markers stop it, and a leading self or cls is not counted. */
final class PositionalParameterCountCheckTest {

  private static final String MESSAGE = "m";
  private static final int ROOMY = 3;

  @Test
  void reportsAFunctionWhosePositionalParametersExceedTheLimit() {
    final SyntaxNode function = function(false, "a", "b", "c");

    assertThat(new PositionalParameterCountCheck(2, MESSAGE).matches(function)).hasSize(1);
    assertThat(new PositionalParameterCountCheck(ROOMY, MESSAGE).matches(function)).isEmpty();
  }

  @Test
  void keywordOnlyAndStarredParametersAreNotCounted() {
    final SyntaxNode function = function(true, "a", "b");

    assertThat(new PositionalParameterCountCheck(2, MESSAGE).matches(function)).isEmpty();
    assertThat(new PositionalParameterCountCheck(1, MESSAGE).matches(function)).hasSize(1);
  }

  @Test
  void aLeadingSelfIsNotCounted() {
    final SyntaxNode method = function(false, "self", "a", "b");

    assertThat(new PositionalParameterCountCheck(2, MESSAGE).matches(method)).isEmpty();
    assertThat(new PositionalParameterCountCheck(1, MESSAGE).matches(method)).hasSize(1);
  }

  /** {@code def f(<names>, *, extra)} when {@code keywordOnly}, else {@code def f(<names>)}. */
  private static SyntaxNode function(final boolean keywordOnly, final String... names) {
    final java.util.List<SyntaxNode> params = new java.util.ArrayList<>();
    for (final String name : names) {
      params.add(Nodes.node("tfpdef", Nodes.leaf(name)));
      params.add(Nodes.leaf(","));
    }
    if (keywordOnly) {
      params.add(Nodes.leaf("*"));
      params.add(Nodes.leaf(","));
      params.add(Nodes.node("tfpdef", Nodes.leaf("extra")));
    }
    final SyntaxNode typed = Nodes.node("typedargslist", params.toArray(SyntaxNode[]::new));
    return Nodes.node(
        "funcdef",
        Nodes.leaf("def"),
        Nodes.leaf("f"),
        Nodes.node("parameters", Nodes.leaf("("), typed, Nodes.leaf(")")),
        Nodes.block(Nodes.leaf("pass")));
  }
}
