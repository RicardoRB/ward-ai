package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The class and interface design checks, run over small hand-made trees. */
class ClassDesignChecksTest extends DesignInterfaceTrees {

  private static final String MESSAGE = "{value}";
  private static final String PUBLIC = "public";
  private static final String PRIVATE = "private";
  private static final String STATIC = "static";
  private static final String FINAL = "final";
  private static final String ABSTRACT = "abstract";
  private static final String INT = "int";

  @Test
  void finalClassNeedsFinalAbstractExtendedOrPublicConstructors() {
    final FinalClassCheck check = new FinalClassCheck(MESSAGE);
    assertThat(check.matches(root(classNode("Util", privateCtor("Util"))))).hasSize(1);
    assertThat(
            check.matches(
                root(classNode(List.of(mod(FINAL)), "FinalUtil", privateCtor("FinalUtil")))))
        .isEmpty();
    assertThat(
            check.matches(
                root(
                    classNode(
                        List.of(mod(ABSTRACT)), "AbstractUtil", privateCtor("AbstractUtil")))))
        .isEmpty();
    final SyntaxNode child = classWithSuper("Child", "Base");
    assertThat(check.matches(root(classNode("Base", privateCtor("Base")), child))).isEmpty();
    final SyntaxNode open = classNode("Open", member(List.of(mod(PUBLIC)), constructor("Open")));
    assertThat(check.matches(root(open))).isEmpty();
    assertThat(check.matches(root(classNode("Plain", member(List.of(), method("run")))))).isEmpty();
  }

  @Test
  void hideUtilityClassConstructorReportsClassesOfStaticMembersOnly() {
    final HideUtilityClassConstructorCheck check =
        new HideUtilityClassConstructorCheck(Set.of("Generated"), MESSAGE);
    final SyntaxNode staticOnly = classNode("Util", member(List.of(mod(STATIC)), method("util")));
    assertThat(check.matches(root(staticOnly))).hasSize(1);
    final SyntaxNode publicCtor =
        classNode(
            "Util2",
            member(List.of(mod(STATIC)), method("util")),
            member(List.of(mod(PUBLIC)), constructor("Util2")));
    assertThat(check.matches(root(publicCtor))).hasSize(1);
    final SyntaxNode privateCtor =
        classNode(
            "Util3",
            member(List.of(mod(STATIC)), method("util")),
            member(List.of(mod(PRIVATE)), constructor("Util3")));
    assertThat(check.matches(root(privateCtor))).isEmpty();
    final SyntaxNode mixed =
        classNode(
            "Mixed",
            member(List.of(mod(STATIC)), method("util")),
            member(List.of(), method("inst")));
    assertThat(check.matches(root(mixed))).isEmpty();
    final SyntaxNode constants = classNode("Consts", member(List.of(mod(STATIC)), field(INT, "X")));
    assertThat(check.matches(root(constants))).hasSize(1);
    final SyntaxNode generated =
        classNode(
            List.of(mod("@Generated")), "Generated1", member(List.of(mod(STATIC)), method("util")));
    assertThat(check.matches(root(generated))).isEmpty();
    assertThat(check.matches(root(classNode("Empty")))).isEmpty();
  }

  @Test
  void innerTypeLastReportsMembersAfterAnInnerType() {
    final InnerTypeLastCheck check = new InnerTypeLastCheck(MESSAGE);
    final SyntaxNode innerFirst =
        classNode("Outer", innerMember(), member(List.of(), method("run")));
    assertThat(check.matches(root(innerFirst))).hasSize(1);
    final SyntaxNode innerLast =
        classNode("Outer", member(List.of(), method("run")), innerMember());
    assertThat(check.matches(root(innerLast))).isEmpty();
    final SyntaxNode withSemicolon =
        classNode(
            "Outer",
            innerMember(),
            member(List.of(), method("run")),
            n("classBodyDeclaration", t("';'", ";")));
    assertThat(check.matches(root(withSemicolon))).hasSize(1);
  }

  @Test
  void interfaceIsTypeReportsInterfacesWithConstantsOnly() {
    final InterfaceIsTypeCheck allowed = new InterfaceIsTypeCheck(true, MESSAGE);
    assertThat(allowed.matches(root(interfaceNode("Api", interfaceMethod("run"))))).isEmpty();
    assertThat(allowed.matches(root(interfaceNode("Constants", interfaceConstant("X")))))
        .hasSize(1);
    final SyntaxNode marker = interfaceNode("Marker");
    assertThat(allowed.matches(root(marker))).isEmpty();
    assertThat(new InterfaceIsTypeCheck(false, MESSAGE).matches(root(marker))).hasSize(1);
  }

  @Test
  void mutableExceptionReportsMutableFieldsOfMatchingExceptions() {
    final MutableExceptionCheck check =
        new MutableExceptionCheck(
            Pattern.compile("Failure$"), Pattern.compile("^Exception$"), MESSAGE);
    final SyntaxNode failure =
        classWithSuper(
            "BadFailure",
            "Exception",
            member(List.of(), field(INT, "code")),
            member(List.of(mod(FINAL)), field(INT, "fixed")),
            member(List.of(mod(STATIC)), field(INT, "count")));
    assertThat(check.matches(root(failure))).hasSize(2);
    final SyntaxNode plain =
        classWithSuper("Plain", "Exception", member(List.of(), field(INT, "x")));
    assertThat(check.matches(root(plain))).isEmpty();
    final SyntaxNode otherSuper =
        classWithSuper("BadFailure", "Thing", member(List.of(), field(INT, "x")));
    assertThat(check.matches(root(otherSuper))).isEmpty();
    final SyntaxNode noSuper = classNode("BadFailure", member(List.of(), field(INT, "x")));
    assertThat(check.matches(root(noSuper))).isEmpty();
  }
}
