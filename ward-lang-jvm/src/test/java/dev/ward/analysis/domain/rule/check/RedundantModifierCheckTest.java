package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.n;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.t;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedundantModifierCheckTest {
  private static final int NUMBER_3 = 3;

  private static final String MESSAGE = "message";
  private static final String MODIFIER = "modifier";
  private static final String INTERFACE_MODIFIER = "interfaceMethodModifier";
  private static final String INTERFACE_BODY = "interfaceBodyDeclaration";
  private static final String INTERFACE_MEMBER = "interfaceMemberDeclaration";
  private static final String CLASS_BODY = "classBodyDeclaration";
  private static final String MEMBER = "memberDeclaration";
  private static final String IDENTIFIER = "IDENTIFIER";

  @Test
  void reportsTheRedundantModifiersOfInterfaceConstantsAndMethods() {
    final SyntaxNode constant =
        n(
            INTERFACE_BODY,
            modifier(MODIFIER, "public"),
            modifier(MODIFIER, "static"),
            modifier(MODIFIER, "final"),
            n(INTERFACE_MEMBER, n("constDeclaration", t(IDENTIFIER, "X"))));
    assertThat(run(constant)).hasSize(NUMBER_3);
    assertThat(
            run(
                n(
                    INTERFACE_BODY,
                    modifier(MODIFIER, "private"),
                    n(INTERFACE_MEMBER, n("constDeclaration")))))
        .isEmpty();
    final SyntaxNode method =
        n(
            "interfaceMethodDeclaration",
            modifier(INTERFACE_MODIFIER, "public"),
            modifier(INTERFACE_MODIFIER, "abstract"),
            t(IDENTIFIER, "run"));
    assertThat(run(n(INTERFACE_BODY, n(INTERFACE_MEMBER, method)))).hasSize(2);
  }

  @Test
  void reportsStaticOnNestedTypesOfAClassAndAnInterface() {
    final SyntaxNode enumHolder =
        n(
            CLASS_BODY,
            modifier(MODIFIER, "static"),
            n(MEMBER, n("enumDeclaration", t(IDENTIFIER, "Color"))));
    final SyntaxNode interfaceHolder =
        n(
            INTERFACE_BODY,
            modifier(MODIFIER, "static"),
            n(INTERFACE_MEMBER, n("interfaceDeclaration")));
    assertThat(run(enumHolder)).hasSize(1);
    assertThat(run(interfaceHolder)).hasSize(1);
  }

  @Test
  void ignoresClassMethodsAndMembersWithoutADeclaration() {
    assertThat(run(n(CLASS_BODY, modifier(MODIFIER, "public"), n(MEMBER, n("methodDeclaration")))))
        .isEmpty();
    assertThat(run(n(INTERFACE_BODY, modifier(MODIFIER, "public"), n(INTERFACE_MEMBER)))).isEmpty();
    assertThat(run(n(INTERFACE_BODY, modifier(MODIFIER, "public"), n("otherDeclaration"))))
        .isEmpty();
  }

  private static List<RuleMatch> run(final SyntaxNode holder) {
    return unitMatches(new RedundantModifierCheck(MESSAGE), holder);
  }

  private static SyntaxNode modifier(final String kind, final String text) {
    return n(kind, t(kind, text));
  }
}
