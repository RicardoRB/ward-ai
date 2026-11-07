package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.n;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.t;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InterfaceMemberImpliedModifierCheckTest {

  private static final String MESSAGE = "message";
  private static final String ABSTRACT_METHOD = "abstractMethod";
  private static final String MODIFIER = "modifier";
  private static final String PUBLIC = "public";
  private static final String STATIC = "static";
  private static final String FINAL = "final";
  private static final String IDENTIFIER = "IDENTIFIER";
  private static final String NAME = "foo";
  private static final String BODYLESS_METHOD = "interfaceMethodDeclaration";
  private static final String CONSTANT = "constDeclaration";
  private static final String NESTED = "classDeclaration";
  private static final String SEMICOLON = "';'";
  private static final InterfaceMemberImpliedModifierCheck.Flags ALL =
      flags(
          ABSTRACT_METHOD,
          "finalField",
          "publicField",
          "publicMethod",
          "publicNested",
          "staticField",
          "staticNested");
  private static final InterfaceMemberImpliedModifierCheck.Flags NONE = flags();

  @Test
  void checksConstantsAgainstTheirOwnFlagsAndTheHolderModifiers() {
    final SyntaxNode constant = holder(member(n(CONSTANT, t(IDENTIFIER, "X"))));
    assertThat(run(ALL, constant)).hasSize(1);
    assertThat(run(flags("publicField"), constant)).hasSize(1);
    assertThat(run(flags("staticField"), constant)).hasSize(1);
    assertThat(run(flags("finalField"), constant)).hasSize(1);
    assertThat(run(NONE, constant)).isEmpty();
    final SyntaxNode complete =
        holder(
            n(MODIFIER, t(MODIFIER, PUBLIC)),
            n(MODIFIER, t(MODIFIER, STATIC)),
            n(MODIFIER, t(MODIFIER, FINAL)),
            member(n(CONSTANT, t(IDENTIFIER, "X"))));
    assertThat(run(ALL, complete)).isEmpty();
  }

  @Test
  void checksMethodsForAbstractAndPublicUnlessDefaultStaticOrPrivate() {
    assertThat(run(flags(ABSTRACT_METHOD), holder(member(methodWith("", NAME))))).hasSize(1);
    assertThat(run(flags(ABSTRACT_METHOD), holder(member(methodWith("default", NAME))))).isEmpty();
    assertThat(run(flags(ABSTRACT_METHOD), holder(member(methodWith("static", NAME))))).isEmpty();
    assertThat(run(flags(ABSTRACT_METHOD), holder(member(methodWith("private", NAME))))).isEmpty();
    assertThat(run(flags(ABSTRACT_METHOD), holder(member(methodWith("abstract", NAME))))).isEmpty();
    assertThat(run(flags("publicMethod"), holder(member(methodWith("", NAME))))).hasSize(1);
    assertThat(run(flags("publicMethod"), holder(member(methodWith("private", NAME))))).isEmpty();
    assertThat(run(flags("publicMethod"), holder(member(methodWith(PUBLIC, NAME))))).isEmpty();
    final SyntaxNode withBody =
        n(BODYLESS_METHOD, t(IDENTIFIER, NAME), n("methodBody", n("block")));
    assertThat(run(flags(ABSTRACT_METHOD), holder(member(withBody)))).isEmpty();
  }

  @Test
  void checksNestedTypesForPublicAndStatic() {
    assertThat(run(ALL, holder(member(n(NESTED, t(IDENTIFIER, "Inner")))))).hasSize(1);
    final SyntaxNode complete =
        holder(
            n(MODIFIER, t(MODIFIER, PUBLIC)), n(MODIFIER, t(MODIFIER, STATIC)), member(n(NESTED)));
    assertThat(run(ALL, complete)).isEmpty();
    assertThat(run(flags("publicNested"), holder(member(n(NESTED))))).hasSize(1);
    assertThat(run(flags("staticNested"), holder(member(n(NESTED))))).hasSize(1);
  }

  @Test
  void ignoresMembersThatAreNotDeclarationsAndEmptyHolders() {
    assertThat(run(ALL, holder(n("otherMember")))).isEmpty();
    assertThat(run(ALL, n("interfaceBodyDeclaration"))).isEmpty();
    assertThat(
            run(ALL, n("interfaceBodyDeclaration", t("'{'", "{"), n("interfaceMemberDeclaration"))))
        .isEmpty();
  }

  private static java.util.List<dev.ward.analysis.domain.rule.RuleMatch> run(
      final InterfaceMemberImpliedModifierCheck.Flags flags, final SyntaxNode holder) {
    return unitMatches(new InterfaceMemberImpliedModifierCheck(flags, MESSAGE), holder);
  }

  private static InterfaceMemberImpliedModifierCheck.Flags flags(final String... enabled) {
    final Set<String> on = Set.of(enabled);
    return new InterfaceMemberImpliedModifierCheck.Flags(
        on.contains(ABSTRACT_METHOD),
        on.contains("finalField"),
        on.contains("publicField"),
        on.contains("publicMethod"),
        on.contains("publicNested"),
        on.contains("staticField"),
        on.contains("staticNested"));
  }

  private static SyntaxNode holder(final SyntaxNode... children) {
    final SyntaxNode[] all = new SyntaxNode[children.length + 1];
    all[0] = t("'{'", "{");
    System.arraycopy(children, 0, all, 1, children.length);
    return n("interfaceBodyDeclaration", all);
  }

  private static SyntaxNode member(final SyntaxNode declaration) {
    return n("interfaceMemberDeclaration", declaration);
  }

  private static SyntaxNode methodWith(final String modifier, final String name) {
    final SyntaxNode body = n("methodBody", t(SEMICOLON, ";"));
    if (modifier.isEmpty()) {
      return n(BODYLESS_METHOD, t(IDENTIFIER, name), body);
    }
    return n(
        BODYLESS_METHOD,
        n("interfaceMethodModifier", t(MODIFIER, modifier)),
        t(IDENTIFIER, name),
        body);
  }
}
