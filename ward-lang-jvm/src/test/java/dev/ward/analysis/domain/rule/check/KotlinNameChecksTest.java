package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin package, file, member and object-property name checks over hand-made trees. */
class KotlinNameChecksTest {

  private static final String MESSAGE = "m";
  private static final String NAME = "simpleIdentifier";
  private static final String IDENTIFIER = "identifier";
  private static final String MODIFIERS = "modifierList";
  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER = "classMemberDeclaration";
  private static final String VARIABLE = "variableDeclaration";
  private static final String TOKEN = "token";
  private static final String OPEN = "'{'";
  private static final String CLOSE = "'}'";
  private static final String FILE_PATH = "src/main/other/Thing.kt";
  private static final String DEV_PATH = "src/dev/x/Thing.kt";
  private static final int LINE = 1;
  private static final int OFFSET = 0;

  private static SyntaxNode leaf(final String kind, final String text) {
    return tok(kind, text, LINE, OFFSET);
  }

  @Test
  void reportsAPackageThatDoesNotMatchTheDirectory() {
    final SyntaxNode file = packageFile("dev", "x");

    assertThat(
            new KotlinPackageDirectoryCheck("", false)
                .check(TreeBuilders.unit(file), new SourceFile(FILE_PATH, "")))
        .hasSize(1);
    assertThat(
            new KotlinPackageDirectoryCheck("", false)
                .check(TreeBuilders.unit(file), new SourceFile(DEV_PATH, "")))
        .isEmpty();
    assertThat(
            new KotlinPackageDirectoryCheck("", false)
                .check(TreeBuilders.unit(file), new SourceFile("", "")))
        .isEmpty();
  }

  @Test
  void reportsAMissingRootPackageWhenRequired() {
    final SyntaxNode file = packageFile("dev", "x");

    assertThat(
            new KotlinPackageDirectoryCheck("com.example", true)
                .check(TreeBuilders.unit(file), new SourceFile(DEV_PATH, "")))
        .hasSize(1);
  }

  @Test
  void reportsASingleTopLevelClassNamedAfterAnotherFile() {
    final SyntaxNode file = topLevelClass("Thing");

    assertThat(
            new KotlinMatchingDeclarationNameCheck(true, MESSAGE)
                .check(TreeBuilders.unit(file), new SourceFile("src/Other.kt", "")))
        .hasSize(1);
    assertThat(
            new KotlinMatchingDeclarationNameCheck(true, MESSAGE)
                .check(TreeBuilders.unit(file), new SourceFile("src/Thing.jvm.kt", "")))
        .isEmpty();
  }

  @Test
  void reportsAMemberNamedAfterItsClassUnlessOverridden() {
    final SyntaxNode clazz = classWithFunction("Thing", "");

    assertThat(unitMatches(new KotlinMemberNameCheck(true, MESSAGE), clazz)).hasSize(1);
    final SyntaxNode overriding = classWithFunction("Thing", "override");
    assertThat(unitMatches(new KotlinMemberNameCheck(true, MESSAGE), overriding)).isEmpty();
    assertThat(unitMatches(new KotlinMemberNameCheck(false, MESSAGE), overriding)).hasSize(1);
  }

  @Test
  void checksConstantsAgainstTheConstantPattern() {
    final KotlinObjectPropertyCheck check =
        new KotlinObjectPropertyCheck("[A-Z_]+", ".*", "_?.*", MESSAGE);

    assertThat(unitMatches(check, propertyIn("objectDeclaration", "max", "const"))).hasSize(1);
    assertThat(unitMatches(check, propertyIn("objectDeclaration", "MAX", "const"))).isEmpty();
    assertThat(unitMatches(check, propertyIn("companionObject", "max", "const"))).hasSize(1);
  }

  private static SyntaxNode propertyIn(final String kind, final String name, final String mod) {
    return group(
        kind,
        group(
            CLASS_BODY,
            leaf(OPEN, "{"),
            group(
                MEMBER,
                group(
                    "propertyDeclaration",
                    group(MODIFIERS, leaf(TOKEN, mod)),
                    group(VARIABLE, leaf(NAME, name))),
                leaf("'val'", "val")),
            leaf(CLOSE, "}")));
  }

  private static SyntaxNode packageFile(final String first, final String second) {
    return group(
        "kotlinFile",
        group(
            "packageHeader",
            leaf("'package'", "package"),
            group(IDENTIFIER, leaf(NAME, first), leaf("'.'", "."), leaf(NAME, second))));
  }

  private static SyntaxNode topLevelClass(final String name) {
    return group(
        "kotlinFile",
        group(
            "topLevelObject",
            group("classDeclaration", leaf("'class'", "class"), leaf(NAME, name))));
  }

  private static SyntaxNode classWithFunction(final String className, final String modifier) {
    final SyntaxNode modifiers =
        modifier.isEmpty()
            ? leaf(TOKEN, "public")
            : group("functionModifierList", leaf(TOKEN, modifier));
    return group(
        "classDeclaration",
        leaf("'class'", "class"),
        leaf(NAME, className),
        group(
            CLASS_BODY,
            leaf(OPEN, "{"),
            group(
                MEMBER,
                group(
                    "functionDeclaration",
                    modifiers,
                    leaf("'fun'", "fun"),
                    group(IDENTIFIER, leaf(NAME, className)))),
            leaf(CLOSE, "}")));
  }
}
