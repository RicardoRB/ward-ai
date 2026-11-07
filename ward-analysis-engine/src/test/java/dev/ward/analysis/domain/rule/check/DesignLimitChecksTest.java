package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class DesignLimitChecksTest extends DesignFlowTrees {

  private static final String TEMPLATE = "{value}/{max}";

  @Test
  void reportsNestedIfAtTheProblemDepthOnly() {
    final SyntaxNode root = compilationUnit(ifStatement("a", ifStatement("b")));

    assertThat(messagesOf(new NestedIfCheck(1, TEMPLATE), root)).containsExactly("1/1");
    assertThat(messagesOf(new NestedIfCheck(2, TEMPLATE), root)).containsExactly("2/2");
    assertThat(messagesOf(new NestedIfCheck(3, TEMPLATE), root)).isEmpty();
  }

  @Test
  void rejectsProblemDepthBelowOne() {
    assertThatThrownBy(() -> new NestedIfCheck(0, TEMPLATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reportsFilesWithEnoughImports() {
    final SyntaxNode two =
        compilationUnit(importDecl("java.util.List"), importDecl("java.util.Map"));
    final SyntaxNode one = compilationUnit(importDecl("java.util.List"));

    assertThat(messagesOf(new ImportCountCheck(2, TEMPLATE), two)).containsExactly("2/2");
    assertThat(messagesOf(new ImportCountCheck(2, TEMPLATE), one)).isEmpty();
  }

  @Test
  void reportsParameterListsAtTheMinimum() {
    final SyntaxNode two = compilationUnit(n("methodDeclaration", parameters("a", "b")));
    final SyntaxNode one = compilationUnit(n("methodDeclaration", parameters("a")));

    assertThat(messagesOf(new ParameterCountCheck(2, TEMPLATE), two)).containsExactly("2/2");
    assertThat(messagesOf(new ParameterCountCheck(2, TEMPLATE), one)).isEmpty();
  }

  @Test
  void ignoresParametersOfPrivateConstructorsOnly() {
    final SyntaxNode privateConstructor =
        compilationUnit(classOf(member(constructor("a", "b"), "private")));
    final SyntaxNode publicConstructor =
        compilationUnit(classOf(member(constructor("a", "b"), "public")));

    assertThat(messagesOf(new ParameterCountCheck(2, TEMPLATE), privateConstructor)).isEmpty();
    assertThat(messagesOf(new ParameterCountCheck(2, TEMPLATE), publicConstructor))
        .containsExactly("2/2");
  }

  @Test
  void countsOnlyNonFinalNonStaticFields() {
    final SyntaxNode mixed =
        compilationUnit(
            classOf(
                member(field("a"), "private"),
                member(field("b"), "final"),
                member(field("c"), "static")));
    final SyntaxNode two = compilationUnit(classOf(member(field("a")), member(field("b"))));

    assertThat(messagesOf(new FieldLimitCheck(1, TEMPLATE), mixed)).isEmpty();
    assertThat(messagesOf(new FieldLimitCheck(1, TEMPLATE), two)).containsExactly("2/1");
  }

  @Test
  void skipsTrivialAccessorsWhenCountingMethods() {
    final SyntaxNode accessorAndMethod =
        compilationUnit(
            classOf(member(method("getName")), member(method("compute", assignment("a", "1")))));
    final SyntaxNode longGetterAndMethod =
        compilationUnit(
            classOf(
                member(method("getName", assignment("a", "1"), assignment("b", "2"))),
                member(method("compute"))));

    assertThat(messagesOf(new MethodLimitCheck(1, TEMPLATE), accessorAndMethod)).isEmpty();
    assertThat(messagesOf(new MethodLimitCheck(1, TEMPLATE), longGetterAndMethod))
        .containsExactly("2/1");
  }

  @Test
  void countsPublicMethodsAndFieldsOfAClass() {
    final SyntaxNode enough =
        compilationUnit(
            classOf(
                member(method("run"), "public"),
                member(field("count"), "public"),
                member(method("hide"), "private")));
    final SyntaxNode notEnough =
        compilationUnit(
            classOf(member(method("run"), "public"), member(method("hide"), "private")));

    assertThat(messagesOf(new PublicCountCheck(2, TEMPLATE), enough)).containsExactly("2/2");
    assertThat(messagesOf(new PublicCountCheck(2, TEMPLATE), notEnough)).isEmpty();
  }

  @Test
  void reportsSwitchesWhoseStatementsPerLabelReachTheThreshold() {
    assertThat(messagesOf(new SwitchDensityCheck(2, TEMPLATE), compilationUnit(switchOf(1, 2))))
        .containsExactly("2/2");
    assertThat(messagesOf(new SwitchDensityCheck(2, TEMPLATE), compilationUnit(switchOf(2, 2))))
        .isEmpty();
    assertThat(messagesOf(new SwitchDensityCheck(2, TEMPLATE), compilationUnit(switchOf(0, 2))))
        .isEmpty();
  }
}
