package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The final-local-variable check on blocks: initialized locals, reassignments and finals. */
class FinalLocalVariableCheckTest extends TreeBuilders {

  private static final String MESSAGE = "final {value}";
  private static final String BLOCK_STATEMENT = "blockStatement";

  @Test
  void initializedLocalNeverReassignedIsReported() {
    final SyntaxNode block = block(initialized(false, "x"), other());
    assertThat(findings(block)).hasSize(1);
  }

  @Test
  void reassignedOrIncrementedLocalsAreNotReported() {
    assertThat(findings(block(initialized(false, "x"), assignmentStatement("x", "y")))).isEmpty();
    assertThat(findings(block(initialized(false, "x"), incrementStatement("x")))).isEmpty();
  }

  @Test
  void finalAndUninitializedLocalsAreNotReported() {
    assertThat(findings(block(initialized(true, "x"), other()))).isEmpty();
    assertThat(findings(block(uninitialized("x"), other()))).isEmpty();
  }

  private static java.util.List<dev.ward.analysis.domain.rule.RuleMatch> findings(
      final SyntaxNode block) {
    return new FinalLocalVariableCheck(MESSAGE).check(unit(block), FILE);
  }

  private static SyntaxNode block(final SyntaxNode... statements) {
    final SyntaxNode[] wrapped = new SyntaxNode[statements.length];
    for (int index = 0; index < statements.length; index++) {
      wrapped[index] = n(BLOCK_STATEMENT, statements[index]);
    }
    return n(BLOCK, wrapped);
  }

  private static SyntaxNode initialized(final boolean isFinal, final String name) {
    final SyntaxNode[] children =
        isFinal
            ? new SyntaxNode[] {
              n("variableModifier", t("'final'", "final")), t("int", "int"), declarator(name, true)
            }
            : new SyntaxNode[] {t("int", "int"), declarator(name, true)};
    return n("localVariableDeclaration", children);
  }

  private static SyntaxNode uninitialized(final String name) {
    return n("localVariableDeclaration", t("int", "int"), declarator(name, false));
  }

  private static SyntaxNode declarator(final String name, final boolean initialized) {
    return initialized
        ? n(
            "variableDeclarator",
            t(TOKEN_IDENTIFIER, name),
            n("variableInitializer", t("INT", "1")))
        : n("variableDeclarator", t(TOKEN_IDENTIFIER, name));
  }

  private static SyntaxNode other() {
    return n(STATEMENT, t(TOKEN_IDENTIFIER, "other"));
  }

  private static SyntaxNode assignmentStatement(final String target, final String value) {
    return n(STATEMENT, assignment(target, value));
  }

  private static SyntaxNode incrementStatement(final String name) {
    return n(STATEMENT, n(EXPRESSION, id(name), t("'++'", "++")));
  }
}
