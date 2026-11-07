package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.CorrectnessTrees.leaf;
import static dev.ward.analysis.domain.rule.check.CorrectnessTrees.node;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class SelfRecursiveMethodCheckTest {

  private static final int BODY = 3;

  private static final SelfRecursiveMethodCheck CHECK = new SelfRecursiveMethodCheck("message");

  private static SyntaxNode method(final String name, final String... body) {
    final SyntaxNode[] tokens = new SyntaxNode[body.length];
    for (int i = 0; i < body.length; i++) {
      tokens[i] = leaf("T", body[i], i + BODY);
    }
    return node(
        "methodDeclaration",
        1,
        node("identifier", 2, leaf("IDENTIFIER", name, 2)),
        node("methodBody", BODY, tokens));
  }

  @Test
  void aFirstStatementThatCallsTheMethodIsReportedWithOrWithoutReturn() {
    assertThat(CHECK.matches(method("depth", "{", "depth", "(", ")", ";", "}"))).hasSize(1);
    assertThat(CHECK.matches(method("depth", "{", "return", "depth", "(", ")", ";", "}")))
        .hasSize(1);
  }

  @Test
  void otherFirstStatementsAreNotReported() {
    assertThat(CHECK.matches(method("depth", "{", "other", "(", ")", ";", "}"))).isEmpty();
    assertThat(CHECK.matches(method("depth", "{", "depthFirst", "(", ")", ";", "}"))).isEmpty();
    assertThat(CHECK.matches(method("depth", "{", "if", "(", "x", ")", "}"))).isEmpty();
  }

  @Test
  void aMethodWithoutANameOrABodyIsSkipped() {
    assertThat(CHECK.matches(node("methodDeclaration", 1))).isEmpty();
    assertThat(
            CHECK.matches(node("methodDeclaration", 1, node("identifier", 2, leaf("I", "f", 2)))))
        .isEmpty();
  }
}
