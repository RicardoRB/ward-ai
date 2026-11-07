package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class OverwrittenParameterCheckTest {

  private static final String COUNT = "count";

  private static final int NAME = 3;
  private static final int BODY = 5;
  private static final OverwrittenParameterCheck CHECK = new OverwrittenParameterCheck("message");

  private static SyntaxNode method(final String parameter, final String... body) {
    final SyntaxNode[] tokens = new SyntaxNode[body.length];
    for (int i = 0; i < body.length; i++) {
      tokens[i] = MoreTrees.leaf("T", body[i], BODY + i);
    }
    final SyntaxNode type =
        MoreTrees.node(
            "typeType", 2, MoreTrees.node("identifier", 2, MoreTrees.leaf("I", "int", 2)));
    final SyntaxNode declared =
        MoreTrees.node(
            "variableDeclaratorId",
            NAME,
            MoreTrees.node("identifier", NAME, MoreTrees.leaf("I", parameter, NAME)));
    return MoreTrees.node(
        "methodDeclaration",
        1,
        MoreTrees.node("formalParameters", 2, MoreTrees.node("formalParameter", 2, type, declared)),
        MoreTrees.node("methodBody", BODY, tokens));
  }

  @Test
  void aFirstStatementThatAssignsAValueNotReadingTheParameterIsReported() {
    assertThat(CHECK.matches(method(COUNT, "{", COUNT, "=", "0", ";", "}"))).hasSize(1);
  }

  @Test
  void aValueThatReadsTheParameterOrAnotherStatementIsNotReported() {
    assertThat(CHECK.matches(method(COUNT, "{", COUNT, "=", COUNT, "+", "1", ";", "}"))).isEmpty();
    assertThat(CHECK.matches(method(COUNT, "{", "other", "=", "0", ";", "}"))).isEmpty();
    assertThat(CHECK.matches(method(COUNT, "{", COUNT, "=", "=", "0", ";", "}"))).isEmpty();
    assertThat(CHECK.matches(method(COUNT, "{", COUNT, "=", "0", "}"))).isEmpty();
  }

  @Test
  void aMethodWithoutParametersOrABodyIsSkipped() {
    assertThat(CHECK.matches(MoreTrees.node("methodDeclaration", 1))).isEmpty();
    assertThat(
            CHECK.matches(
                MoreTrees.node(
                    "methodDeclaration",
                    1,
                    MoreTrees.node("formalParameter", 2),
                    MoreTrees.node("methodBody", BODY))))
        .isEmpty();
  }
}
