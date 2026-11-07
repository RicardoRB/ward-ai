package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.check.VisibilityModifierCheck.FieldVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The class design checks, run over small hand-made trees with the shapes the parser produces. */
class ClassDesignChecksTest extends TreeBuilders {

  private static final String MESSAGE = "{value}";
  private static final String PUBLIC = "public";
  private static final String PRIVATE = "private";
  private static final String PROTECTED = "protected";
  private static final String STATIC = "static";
  private static final String FINAL = "final";
  private static final String ABSTRACT = "abstract";
  private static final String VOID = "void";
  private static final String INT = "int";
  private static final String STRING = "String";
  private static final String ID_PATTERN = "^ID$";
  private static final String PHRASE = "extension point";
  private static final int MAX_THROWS = 2;
  private static final int THROWN = 3;
  private static final int UNIT_LENGTH = 100;

  @Test
  void oneTopLevelClassReportsEveryTypeAfterTheFirst() {
    final SyntaxNode two = root(classNode("First"), classNode("Second"));
    assertThat(new OneTopLevelClassCheck(MESSAGE).matches(two)).hasSize(1);
    assertThat(new OneTopLevelClassCheck(MESSAGE).matches(root(classNode("Only")))).isEmpty();
  }

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
    assertThat(
            check.matches(
                root(classNode("Open", member(List.of(mod(PUBLIC)), constructor("Open"))))))
        .isEmpty();
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
    assertThat(
            check.matches(
                root(classWithSuper("Plain", "Exception", member(List.of(), field(INT, "x"))))))
        .isEmpty();
    assertThat(
            check.matches(
                root(classWithSuper("BadFailure", "Thing", member(List.of(), field(INT, "x"))))))
        .isEmpty();
    assertThat(check.matches(root(classNode("BadFailure", member(List.of(), field(INT, "x"))))))
        .isEmpty();
  }

  @Test
  void sealedTypesNeedAPermitsList() {
    final SealedShouldHavePermitsListCheck check = new SealedShouldHavePermitsListCheck(MESSAGE);
    assertThat(check.matches(root(sealedInterface("Shape", false)))).hasSize(1);
    assertThat(check.matches(root(sealedInterface("Closed", true)))).isEmpty();
    assertThat(check.matches(root(classNode("Plain")))).isEmpty();
  }

  @Test
  void throwsCountReportsMethodsOverTheLimit() {
    final SyntaxNode service =
        classNode(
            "Service",
            member(List.of(), throwing("many", THROWN)),
            member(List.of(), throwing("few", MAX_THROWS)),
            member(List.of(mod(PRIVATE)), throwing("hidden", THROWN)));
    final SyntaxNode unit = root(service);
    assertThat(new ThrowsCountCheck(MAX_THROWS, true, MESSAGE).matches(unit)).hasSize(1);
    assertThat(new ThrowsCountCheck(MAX_THROWS, false, MESSAGE).matches(unit)).hasSize(2);
  }

  @Test
  void visibilityModifierReportsFieldsOutsideTheAllowedVisibility() {
    final SyntaxNode holder =
        classNode(
            "Holder",
            member(List.of(mod(PRIVATE)), field(INT, "a")),
            member(List.of(mod(PUBLIC)), field(INT, "b")),
            member(List.of(mod(PROTECTED)), field(INT, "d")),
            member(List.of(), field(INT, "e")),
            member(List.of(mod(PUBLIC), mod(STATIC), mod(FINAL)), field(INT, "K")),
            member(List.of(mod("@Inject"), mod(PUBLIC)), field(INT, "h")),
            member(List.of(mod(PUBLIC)), field(INT, "ID")));
    final VisibilityModifierCheck strict =
        new VisibilityModifierCheck(visibility(false, false, false, false, Set.of()), MESSAGE);
    assertThat(strict.matches(root(holder))).hasSize(3);
    final VisibilityModifierCheck relaxed =
        new VisibilityModifierCheck(visibility(true, true, true, true, Set.of(STRING)), MESSAGE);
    final SyntaxNode relaxedHolder =
        classNode(
            "Relaxed",
            member(List.of(mod(PUBLIC), mod(FINAL)), field(INT, "c")),
            member(List.of(mod(PUBLIC)), field(STRING, "s")),
            member(List.of(mod(PUBLIC)), field(STRING + "[]", "arr")),
            member(List.of(mod(PROTECTED)), field(INT, "d")),
            member(List.of(), field(INT, "e")));
    assertThat(relaxed.matches(root(relaxedHolder))).hasSize(1);
  }

  @Test
  void designForExtensionReportsUndocumentedOverridableMethodsOfPublicClasses() {
    final DesignForExtensionCheck check =
        new DesignForExtensionCheck(Set.of("Override"), Pattern.compile(PHRASE), MESSAGE);
    final SyntaxNode hooks =
        classNode(
            List.of(mod(PUBLIC)),
            "Hooks",
            member(List.of(mod(PUBLIC)), methodWithBody("open")),
            comment("/** An extension point. */"),
            member(List.of(mod(PUBLIC)), methodWithBody("documented")),
            comment("/** Opens. */"),
            member(List.of(mod(PUBLIC)), methodWithBody("wrongText")),
            member(List.of(mod(PRIVATE)), methodWithBody("hidden")),
            member(List.of(mod(STATIC)), methodWithBody("stat")),
            member(List.of(mod(FINAL)), methodWithBody("fixed")),
            member(List.of(mod(ABSTRACT)), method("abs")),
            member(List.of(mod(PUBLIC)), emptyMethod("nothing")),
            member(List.of(mod("@Override"), mod(PUBLIC)), methodWithBody("ignored")));
    assertThat(check.matches(root(hooks))).hasSize(2);
    final SyntaxNode hidden =
        classNode("Hidden", member(List.of(mod(PUBLIC)), methodWithBody("run")));
    assertThat(check.matches(root(hidden))).isEmpty();
    final SyntaxNode finalClass =
        classNode(
            List.of(mod(PUBLIC), mod(FINAL)),
            "Closed",
            member(List.of(mod(PUBLIC)), methodWithBody("run")));
    assertThat(check.matches(root(finalClass))).isEmpty();
    final SyntaxNode shell =
        classNode(
            "Shell",
            member(
                List.of(mod(PUBLIC)),
                classDecl("Inner", member(List.of(mod(PUBLIC)), methodWithBody("run")))));
    assertThat(check.matches(root(shell))).isEmpty();
  }

  @Test
  void declarationTextReadsSimpleNamesAndAnnotations() {
    assertThat(DeclarationText.simpleName("java.util.List<String>")).isEqualTo("List");
    assertThat(DeclarationText.annotationNames(List.of("@Named(\"x\")", "public")))
        .containsExactly("Named");
  }

  // --- tree builders -----------------------------------------------------------------------

  private static SyntaxNode root(final SyntaxNode... declarations) {
    return Trees.node("compilationunit", 0, UNIT_LENGTH, 1, declarations);
  }

  private static SyntaxNode mod(final String text) {
    return n("modifier", t("'" + text + "'", text));
  }

  private static SyntaxNode classDecl(final String name, final SyntaxNode... members) {
    return n("classDeclaration", t("'class'", "class"), id(name), n("classBody", members));
  }

  private static SyntaxNode classNode(final String name, final SyntaxNode... members) {
    return classNode(List.of(), name, members);
  }

  private static SyntaxNode classNode(
      final List<SyntaxNode> modifiers, final String name, final SyntaxNode... members) {
    return typeDeclaration(modifiers, classDecl(name, members));
  }

  private static SyntaxNode classWithSuper(
      final String name, final String superName, final SyntaxNode... members) {
    final SyntaxNode decl =
        n(
            "classDeclaration",
            t("'class'", "class"),
            id(name),
            n("typeType", t(TOKEN_IDENTIFIER, superName)),
            n("classBody", members));
    return typeDeclaration(List.of(), decl);
  }

  private static SyntaxNode typeDeclaration(
      final List<SyntaxNode> modifiers, final SyntaxNode decl) {
    final List<SyntaxNode> children = new ArrayList<>(modifiers);
    children.add(decl);
    return n("typeDeclaration", children.toArray(SyntaxNode[]::new));
  }

  private static SyntaxNode member(final List<SyntaxNode> modifiers, final SyntaxNode declaration) {
    final List<SyntaxNode> children = new ArrayList<>(modifiers);
    children.add(n("memberDeclaration", declaration));
    return n("classBodyDeclaration", children.toArray(SyntaxNode[]::new));
  }

  private static SyntaxNode innerMember() {
    return member(List.of(), classDecl("Inner"));
  }

  private static SyntaxNode comment(final String text) {
    return Trees.leaf("comment", text, 0);
  }

  private static SyntaxNode method(final String name) {
    return n("methodDeclaration", t("'void'", VOID), id(name));
  }

  private static SyntaxNode methodWithBody(final String name) {
    return n(
        "methodDeclaration",
        t("'void'", VOID),
        id(name),
        n(
            "methodBody",
            t("'{'", "{"),
            n("statement", t(TOKEN_IDENTIFIER, "run();")),
            t("'}'", "}")));
  }

  private static SyntaxNode emptyMethod(final String name) {
    return n(
        "methodDeclaration",
        t("'void'", VOID),
        id(name),
        n("methodBody", t("'{'", "{"), t("'}'", "}")));
  }

  private static SyntaxNode privateCtor(final String name) {
    return member(List.of(mod(PRIVATE)), constructor(name));
  }

  private static SyntaxNode constructor(final String name) {
    return n("constructorDeclaration", id(name));
  }

  private static SyntaxNode field(final String type, final String name) {
    return n(
        "fieldDeclaration",
        n("typeType", t(TOKEN_IDENTIFIER, type)),
        n("variableDeclarators", n("variableDeclarator", n("variableDeclaratorId", id(name)))));
  }

  private static SyntaxNode throwing(final String name, final int count) {
    final SyntaxNode[] exceptions = new SyntaxNode[count];
    for (int index = 0; index < count; index++) {
      exceptions[index] = n("qualifiedName", id("E" + index));
    }
    return n("methodDeclaration", t("'void'", VOID), id(name), n("qualifiedNameList", exceptions));
  }

  private static SyntaxNode interfaceNode(final String name, final SyntaxNode... members) {
    return n(
        "interfaceDeclaration",
        t("'interface'", "interface"),
        id(name),
        n("interfaceBody", members));
  }

  private static SyntaxNode interfaceMethod(final String name) {
    return n(
        "interfaceBodyDeclaration",
        n(
            "interfaceMemberDeclaration",
            n("interfaceMethodDeclaration", t("'void'", VOID), id(name))));
  }

  private static SyntaxNode interfaceConstant(final String name) {
    return n(
        "interfaceBodyDeclaration",
        n(
            "interfaceMemberDeclaration",
            n(
                "constDeclaration",
                n("typeType", t(TOKEN_IDENTIFIER, INT)),
                n("constantDeclarator", id(name)))));
  }

  private static SyntaxNode sealedInterface(final String name, final boolean permits) {
    final List<SyntaxNode> children = new ArrayList<>();
    children.add(t("'interface'", "interface"));
    children.add(id(name));
    if (permits) {
      children.add(t("'permits'", "permits"));
    }
    children.add(n("interfaceBody"));
    return n(
        "typeDeclaration",
        n("classOrInterfaceModifier", t("'sealed'", "sealed")),
        n("interfaceDeclaration", children.toArray(SyntaxNode[]::new)));
  }

  private static FieldVisibility visibility(
      final boolean finalFields,
      final boolean immutable,
      final boolean packageAllowed,
      final boolean protectedAllowed,
      final Set<String> immutableTypes) {
    return new FieldVisibility(
        finalFields,
        immutable,
        packageAllowed,
        protectedAllowed,
        Pattern.compile(ID_PATTERN),
        Set.of("Inject"),
        immutableTypes);
  }
}
