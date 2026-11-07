package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The usage-distance check on small blocks: distance, finals, and uses inside nested blocks. */
class VariableDeclarationUsageDistanceCheckTest extends TreeBuilders {

  private static final String MESSAGE = "{value} is {max} away";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String STATEMENT = "statement";
  private static final String LOCAL = "localVariableDeclaration";
  private static final String FINAL_MODIFIER = "variableModifier";

  @Test
  void distanceBeyondTheAllowedNumberOfStatementsIsReported() {
    final SyntaxNode far = block(decl(false, "first"), other(), other(), other(), use("first"));
    assertThat(Trees.messages(check(2, false).check(unit(far), FILE)))
        .containsExactly("first is 2 away");
    final SyntaxNode near = block(decl(false, "first"), other(), use("first"));
    assertThat(check(2, false).check(unit(near), FILE)).isEmpty();
  }

  @Test
  void finalDeclarationsAreSkippedUnlessTheOptionTurnsThatOff() {
    final SyntaxNode far = block(decl(true, "first"), other(), other(), other(), use("first"));
    assertThat(check(2, true).check(unit(far), FILE)).isEmpty();
    assertThat(check(2, false).check(unit(far), FILE)).hasSize(1);
  }

  @Test
  void firstUseInsideANestedBlockIsNotReported() {
    final SyntaxNode nested =
        block(decl(false, "first"), other(), other(), other(), nestedUse("first"));
    assertThat(check(2, false).check(unit(nested), FILE)).isEmpty();
  }

  @Test
  void neverUsedVariableIsNotReported() {
    final SyntaxNode unused = block(decl(false, "first"), other(), other(), other());
    assertThat(check(2, false).check(unit(unused), FILE)).isEmpty();
  }

  private static VariableDeclarationUsageDistanceCheck check(
      final int allowed, final boolean ignoreFinals) {
    return new VariableDeclarationUsageDistanceCheck(allowed, ignoreFinals, MESSAGE);
  }

  private static SyntaxNode block(final SyntaxNode... statements) {
    final SyntaxNode[] wrapped = new SyntaxNode[statements.length];
    for (int index = 0; index < statements.length; index++) {
      wrapped[index] = n(BLOCK_STATEMENT, statements[index]);
    }
    return n(BLOCK, wrapped);
  }

  private static SyntaxNode decl(final boolean isFinal, final String name) {
    final SyntaxNode local =
        isFinal
            ? n(
                LOCAL,
                n(FINAL_MODIFIER, t("'final'", "final")),
                t("int", "int"),
                n("variableDeclarator", t(TOKEN_IDENTIFIER, name)))
            : n(LOCAL, t("int", "int"), n("variableDeclarator", t(TOKEN_IDENTIFIER, name)));
    return local;
  }

  private static SyntaxNode other() {
    return n(STATEMENT, t(TOKEN_IDENTIFIER, "other"));
  }

  private static SyntaxNode use(final String name) {
    return n(STATEMENT, n(EXPRESSION, t(TOKEN_IDENTIFIER, name)));
  }

  private static SyntaxNode nestedUse(final String name) {
    return n(STATEMENT, t("'if'", "if"), n(BLOCK, n(BLOCK_STATEMENT, use(name))));
  }
}
