package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The names and nodes that the function-body helper finds, leaving nested scopes out. */
final class FunctionBodiesTest {

  @Test
  void assignedNamesIncludeAssignmentsAndLoopTargetsButNotNestedScopes() {
    final SyntaxNode function =
        firstFunction(
            ParsedPython.tree(
                "def f():\n    x = 1\n    for i in y:\n        pass\n    def g():\n        z = 2\n"));

    assertThat(FunctionBodies.assignedNames(function)).containsExactlyInAnyOrder("x", "i");
  }

  @Test
  void parameterNamesReadEveryParameterWithoutItsAnnotationOrDefault() {
    final SyntaxNode function =
        firstFunction(ParsedPython.tree("def f(a, b: int, *args, c=1):\n    pass\n"));

    assertThat(FunctionBodies.parameterNames(function))
        .containsExactlyInAnyOrder("a", "b", "args", "c");
  }

  @Test
  void namesKeepOnlyPlainIdentifiersOfATargetList() {
    assertThat(FunctionBodies.names("a,(b),[c],*d,e.f,x[0],1x"))
        .containsExactly("a", "b", "c", "d");
  }

  @Test
  void ownFindsNothingInsideNestedScopesAndChildReturnsAbsentKinds() {
    final SyntaxNode function =
        firstFunction(ParsedPython.tree("def f():\n    def g():\n        return 1\n"));

    assertThat(FunctionBodies.ownScope(function, Set.of("return_stmt"))).isEmpty();
    assertThat(FunctionBodies.child(function, "no_such_kind")).isEmpty();
    assertThat(FunctionBodies.child(function, "block")).isPresent();
  }

  /** The first function of a module, which is the statement's wrapped definition. */
  private static SyntaxNode firstFunction(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of("funcdef")).get(0).node();
  }
}
