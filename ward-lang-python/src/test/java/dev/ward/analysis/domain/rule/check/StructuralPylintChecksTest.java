package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Pylint checks that read the tree's shape: init generators, unassigned calls, ternaries. */
final class StructuralPylintChecksTest {

  private static final String MESSAGE = "m";

  private static SyntaxNode name(final String text) {
    return new SyntaxNode("NAME", Nodes.at(0, text.length()), Map.of("text", text), List.of());
  }

  private static SyntaxNode function(final String name, final SyntaxNode... body) {
    return Nodes.node("funcdef", name(name), Nodes.block(body));
  }

  private static SyntaxNode call(final String callee) {
    return Nodes.node(
        "atom_expr", Nodes.leaf(callee), Nodes.node("trailer", Nodes.leaf("("), Nodes.leaf(")")));
  }

  @Test
  void initGeneratorReportsAYieldInTheMethodOfAClass() {
    final SyntaxNode method = function("__init__", Nodes.node("yield_expr", Nodes.leaf("yield")));
    assertThat(new InitGeneratorCheck(MESSAGE).matches(Nodes.node("classdef", Nodes.block(method))))
        .hasSize(1);
  }

  @Test
  void initGeneratorIgnoresAYieldInANestedFunctionAndAFunctionOutsideAClass() {
    final SyntaxNode nested =
        function("__init__", function("inner", Nodes.node("yield_expr", Nodes.leaf("yield"))));
    assertThat(new InitGeneratorCheck(MESSAGE).matches(Nodes.node("classdef", Nodes.block(nested))))
        .isEmpty();
    final SyntaxNode module = function("__init__", Nodes.node("yield_expr", Nodes.leaf("yield")));
    assertThat(new InitGeneratorCheck(MESSAGE).matches(Nodes.block(module))).isEmpty();
  }

  @Test
  void unassignedCallReportsAComparisonThatContainsACall() {
    final SyntaxNode comparison =
        Nodes.node("comparison", Nodes.leaf("x"), Nodes.leaf("=="), call("f"));
    assertThat(
            new UnassignedCallExpressionCheck(MESSAGE).matches(Nodes.node("expr_stmt", comparison)))
        .hasSize(1);
  }

  @Test
  void unassignedCallIgnoresABareCallAndAnAssignment() {
    assertThat(
            new UnassignedCallExpressionCheck(MESSAGE).matches(Nodes.node("expr_stmt", call("f"))))
        .isEmpty();
    final SyntaxNode assignment =
        Nodes.node("expr_stmt", Nodes.leaf("x"), Nodes.leaf("="), call("f"));
    assertThat(new UnassignedCallExpressionCheck(MESSAGE).matches(assignment)).isEmpty();
  }

  @Test
  void andOrTernaryReportsAReturnWithATruthyLiteralMiddle() {
    final SyntaxNode value =
        Nodes.node(
            "or_test",
            Nodes.node("and_test", Nodes.leaf("a"), Nodes.leaf("and"), Nodes.leaf("1")),
            Nodes.leaf("or"),
            Nodes.leaf("c"));
    assertThat(
            new AndOrTernaryCheck(MESSAGE)
                .matches(Nodes.node("return_stmt", Nodes.leaf("return"), value)))
        .hasSize(1);
  }

  @Test
  void andOrTernaryIgnoresAFalsyMiddleLiteral() {
    final SyntaxNode value =
        Nodes.node(
            "or_test",
            Nodes.node("and_test", Nodes.leaf("a"), Nodes.leaf("and"), Nodes.leaf("0")),
            Nodes.leaf("or"),
            Nodes.leaf("c"));
    assertThat(
            new AndOrTernaryCheck(MESSAGE)
                .matches(Nodes.node("return_stmt", Nodes.leaf("return"), value)))
        .isEmpty();
  }

  @Test
  void sysExitIsExemptWhenExitIsImportedFromSys() {
    final Binding fromSys =
        new Binding(
            "exit",
            BindingKind.IMPORT,
            Nodes.at(0, 4),
            Set.of(),
            0,
            Optional.of(new ImportOrigin("sys", "exit", Nodes.at(0, 4))));
    final Binding otherModule =
        new Binding(
            "exit",
            BindingKind.IMPORT,
            Nodes.at(0, 4),
            Set.of(),
            0,
            Optional.of(new ImportOrigin("os", "exit", Nodes.at(0, 4))));
    assertThat(SysExitCallCheck.importsExit(List.of(fromSys))).isTrue();
    assertThat(SysExitCallCheck.importsExit(List.of(otherModule))).isFalse();
    assertThat(SysExitCallCheck.importsExit(List.of())).isFalse();
  }

  @Test
  void threadCallNeedsTargetOrTwoPositionalArguments() {
    assertThat(ThreadInstantiationCheck.isThreadCall(Nodes.leaf("Thread()"))).isTrue();
    assertThat(ThreadInstantiationCheck.isThreadCall(Nodes.leaf("Thread(None)"))).isTrue();
    assertThat(ThreadInstantiationCheck.isThreadCall(Nodes.leaf("Thread(None,f)"))).isFalse();
    assertThat(ThreadInstantiationCheck.isThreadCall(Nodes.leaf("Thread(target=f)"))).isFalse();
  }

  @Test
  void superArgumentsStopAtTheMatchingParenthesis() {
    assertThat(BadSuperCallCheck.superArguments("super(A,self).f()")).containsExactly("A", "self");
    assertThat(BadSuperCallCheck.superArguments("super(type(self),self).f()"))
        .containsExactly("type(self)", "self");
    assertThat(BadSuperCallCheck.superArguments("super()")).isEmpty();
  }
}
