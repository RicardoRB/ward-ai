package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GodClassCheckTest extends ClassStatementTrees {

  private static final String MESSAGE = "{value}";
  private static final String GOD_METRICS = "WMC 48, ATFD 6, TCC 0%";
  private static final String ONLY_METHOD_METRICS = "WMC 47, ATFD 6, TCC 0%";
  private static final String CONDITIONAL_METRICS = "WMC 51, ATFD 6, TCC 0%";

  @Test
  void reportsGodClassWithHighComplexityForeignAccessAndNoCohesion() {
    final SyntaxNode root =
        compilationUnit(classNamed(CLASS_NAME, accessesMethod(false), branchesMethod(false)));

    assertThat(god(root, foreignTypes())).containsExactly(GOD_METRICS);
  }

  @Test
  void reportsGodClassWhenMethodsSeldomShareFields() {
    final SyntaxNode root =
        compilationUnit(
            classNamed(CLASS_NAME, fieldMember(), accessesMethod(false), branchesMethod(false)));

    assertThat(god(root, foreignTypes())).hasSize(1);
  }

  @Test
  void skipsGodClassWhenMethodsShareFields() {
    final SyntaxNode root =
        compilationUnit(
            classNamed(
                CLASS_NAME,
                fieldMember(),
                usingMethod("first"),
                usingMethod("second"),
                accessesMethod(true),
                branchesMethod(true)));

    assertThat(god(root, foreignTypes())).isEmpty();
  }

  @Test
  void skipsGodClassWithoutTypeFacts() {
    final SyntaxNode root =
        compilationUnit(classNamed(CLASS_NAME, accessesMethod(false), branchesMethod(false)));

    assertThat(god(root, UnitTypeFacts.none())).isEmpty();
  }

  @Test
  void skipsGodClassWithLowComplexity() {
    final SyntaxNode root = compilationUnit(classNamed(CLASS_NAME, accessesMethod(false)));

    assertThat(god(root, foreignTypes())).isEmpty();
  }

  @Test
  void ignoresThisAsAForeignReceiver() {
    final SyntaxNode own =
        member(method("read", 0, 0, access("this", "a"), call("this", LOOKUP)), PUBLIC);
    final SyntaxNode root = compilationUnit(classNamed(CLASS_NAME, own, branchesMethod(false)));

    assertThat(god(root, typesOf(OWN))).isEmpty();
  }

  @Test
  void reportsGodClassWhoseOnlyMethodHasNoOtherMethodsToShareWith() {
    final List<SyntaxNode> body = new ArrayList<>();
    for (int index = 0; index < ACCESSES; index++) {
      body.add(access(FOREIGN, ACCESS_PREFIX + index));
    }
    final SyntaxNode only =
        member(method("heavy", 0, DECISIONS_FOR_WMC_47, body.toArray(SyntaxNode[]::new)), PUBLIC);
    final SyntaxNode root = compilationUnit(classNamed(CLASS_NAME, only));

    assertThat(god(root, foreignTypes())).containsExactly(ONLY_METHOD_METRICS);
  }

  @Test
  void countsConditionalOperatorsAsDecisionsOnlyInsideExpressions() {
    final SyntaxNode inExpression =
        member(
            method(
                "pick",
                0,
                0,
                node(EXPRESSION, LINE, COLUMN, leaf("'?'", "?", LINE, COLUMN)),
                node(EXPRESSION, LINE, COLUMN, leaf("'?'", "?", LINE, COLUMN))),
            PUBLIC);
    final SyntaxNode root =
        compilationUnit(
            classNamed(CLASS_NAME, inExpression, branchesMethod(false), accessesMethod(false)));

    assertThat(god(root, foreignTypes())).containsExactly(CONDITIONAL_METRICS);
  }

  @Test
  void skipsGodClassWhoseForeignCallsAreNotDataAccesses() {
    final List<SyntaxNode> calls = new ArrayList<>();
    for (int index = 0; index < ACCESSES; index++) {
      calls.add(call(FOREIGN, COMPUTE));
    }
    final SyntaxNode computing =
        member(method(RUN, 0, 0, calls.toArray(SyntaxNode[]::new)), PUBLIC);
    final SyntaxNode root =
        compilationUnit(classNamed(CLASS_NAME, computing, branchesMethod(false)));

    assertThat(god(root, foreignTypes())).isEmpty();
  }

  private static List<String> god(final SyntaxNode root, final UnitTypeFacts types) {
    return Trees.messages(new GodClassCheck(MESSAGE).matches(root, null, null, types, null));
  }
}
