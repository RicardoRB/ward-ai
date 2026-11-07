package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The clone and finalize checks on small methods: names, parameters and the super call. */
class ObjectMethodCheckTest extends TreeBuilders {

  private static final String FINALIZE = "finalize";

  @Test
  void methodWithTheNameAndNoParametersIsReported() {
    assertThat(
            new ObjectMethodCheck(FINALIZE, false, MESSAGE)
                .check(unit(method(FINALIZE, 0, true, false)), FILE))
        .hasSize(1);
  }

  @Test
  void otherNamesAndMethodsWithParametersAreNotReported() {
    assertThat(
            new ObjectMethodCheck(FINALIZE, false, MESSAGE)
                .check(unit(method("close", 0, true, false)), FILE))
        .isEmpty();
    assertThat(
            new ObjectMethodCheck(FINALIZE, false, MESSAGE)
                .check(unit(method(FINALIZE, 1, true, false)), FILE))
        .isEmpty();
  }

  @Test
  void superCallRuleReportsOnlyBodiesWithoutTheCall() {
    final ObjectMethodCheck check = new ObjectMethodCheck(FINALIZE, true, MESSAGE);
    assertThat(check.check(unit(method(FINALIZE, 0, true, false)), FILE)).hasSize(1);
    assertThat(check.check(unit(method(FINALIZE, 0, true, true)), FILE)).isEmpty();
    assertThat(check.check(unit(method(FINALIZE, 0, false, false)), FILE)).isEmpty();
  }

  private static SyntaxNode method(
      final String name, final int parameters, final boolean body, final boolean callsSuper) {
    final SyntaxNode[] formals = new SyntaxNode[parameters];
    for (int index = 0; index < parameters; index++) {
      formals[index] = n(FORMAL_PARAMETER, t("int", "int"), id("p" + index));
    }
    final SyntaxNode call =
        callsSuper
            ? n(
                STATEMENT,
                t("'super'", "super"),
                t(TOKEN_DOT, "."),
                id(name),
                t(TOKEN_OPEN, "("),
                t(TOKEN_CLOSE, ")"))
            : n(STATEMENT, id("cleanup"));
    return body
        ? n(METHOD_DECLARATION, id(name), n(FORMAL_PARAMETERS, formals), n(BLOCK, call))
        : n(METHOD_DECLARATION, id(name), n(FORMAL_PARAMETERS, formals));
  }
}
