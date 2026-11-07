package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The constructor-grouping and overload-order checks on small class bodies. */
class DeclarationGroupingChecksTest extends TreeBuilders {

  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String COMPACT = "compactConstructorDeclaration";
  private static final String RUN = "run";

  @Test
  void constructorSeparatedFromTheOthersIsReported() {
    final SyntaxNode body = n(CLASS_BODY, constructor(0), method(RUN, 0), constructor(0));
    assertThat(new ConstructorsDeclarationGroupingCheck(false, MESSAGE).check(unit(body), FILE))
        .hasSize(1);
    final SyntaxNode together = n(CLASS_BODY, constructor(0), constructor(1), method(RUN, 0));
    assertThat(new ConstructorsDeclarationGroupingCheck(false, MESSAGE).check(unit(together), FILE))
        .isEmpty();
  }

  @Test
  void emptyMembersAndCommentsDoNotSeparateConstructors() {
    final SyntaxNode body =
        n(
            CLASS_BODY,
            constructor(0),
            n(CLASS_BODY_DECLARATION, t("';'", ";")),
            t("comment", "//"),
            t("';'", ";"),
            n(COMPACT));
    assertThat(new ConstructorsDeclarationGroupingCheck(false, MESSAGE).check(unit(body), FILE))
        .isEmpty();
  }

  @Test
  void constructorsOutOfParameterOrderAreReportedOnlyWithTheOption() {
    final SyntaxNode body = n(CLASS_BODY, constructor(2), constructor(1));
    assertThat(new ConstructorsDeclarationGroupingCheck(true, MESSAGE).check(unit(body), FILE))
        .hasSize(1);
    assertThat(new ConstructorsDeclarationGroupingCheck(false, MESSAGE).check(unit(body), FILE))
        .isEmpty();
  }

  @Test
  void overloadsSeparatedByAnotherMethodAreReported() {
    final SyntaxNode body = n(CLASS_BODY, method(RUN, 0), method("stop", 0), method(RUN, 1));
    assertThat(new OverloadMethodsDeclarationOrderCheck(false, MESSAGE).check(unit(body), FILE))
        .hasSize(1);
    final SyntaxNode grouped = n(CLASS_BODY, method(RUN, 0), method(RUN, 1), method("stop", 0));
    assertThat(new OverloadMethodsDeclarationOrderCheck(false, MESSAGE).check(unit(grouped), FILE))
        .isEmpty();
  }

  @Test
  void overloadsOutOfParameterOrderAreReportedOnlyWithTheOption() {
    final SyntaxNode body = n(CLASS_BODY, method(RUN, 2), method(RUN, 1), t("';'", ";"));
    assertThat(new OverloadMethodsDeclarationOrderCheck(true, MESSAGE).check(unit(body), FILE))
        .hasSize(1);
    assertThat(new OverloadMethodsDeclarationOrderCheck(false, MESSAGE).check(unit(body), FILE))
        .isEmpty();
  }

  private static SyntaxNode constructor(final int parameters) {
    return n(CLASS_BODY_DECLARATION, n(CONSTRUCTOR, id("C"), formals(parameters)));
  }

  private static SyntaxNode method(final String name, final int parameters) {
    return n(CLASS_BODY_DECLARATION, n(METHOD_DECLARATION, id(name), formals(parameters)));
  }

  private static SyntaxNode formals(final int count) {
    final SyntaxNode[] parameters = new SyntaxNode[count];
    for (int index = 0; index < count; index++) {
      parameters[index] = n(FORMAL_PARAMETER, t("int", "int"), id("p" + index));
    }
    return n(FORMAL_PARAMETERS, parameters);
  }
}
