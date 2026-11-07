package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.check.VisibilityModifierCheck.FieldVisibility;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The type-level and member-level design checks that are not class or interface shapes. */
class TypeDesignChecksTest extends DesignInterfaceTrees {

  private static final String MESSAGE = "{value}";
  private static final String PUBLIC = "public";
  private static final String PRIVATE = "private";
  private static final String PROTECTED = "protected";
  private static final String STATIC = "static";
  private static final String FINAL = "final";
  private static final String ABSTRACT = "abstract";
  private static final String INT = "int";
  private static final String STRING = "String";
  private static final String PHRASE = "extension point";
  private static final String ID_PATTERN = "^ID$";
  private static final int MAX_THROWS = 2;
  private static final int THROWN = 3;
  private static final int TWO = 2;
  private static final int STRICT_FINDINGS = 3;

  @Test
  void oneTopLevelClassReportsEveryTypeAfterTheFirst() {
    assertThat(
            new OneTopLevelClassCheck(MESSAGE)
                .matches(root(classNode("First"), classNode("Second"))))
        .hasSize(1);
    assertThat(new OneTopLevelClassCheck(MESSAGE).matches(root(classNode("Only")))).isEmpty();
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
    assertThat(new ThrowsCountCheck(MAX_THROWS, false, MESSAGE).matches(unit)).hasSize(TWO);
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
    assertThat(strict.matches(root(holder))).hasSize(STRICT_FINDINGS);
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
    final SyntaxNode closed =
        classNode(
            List.of(mod(PUBLIC), mod(FINAL)),
            "Closed",
            member(List.of(mod(PUBLIC)), methodWithBody("run")));
    assertThat(check.matches(root(closed))).isEmpty();
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
