package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class DesignMemberChecksTest extends DesignFlowTrees {

  private static final String MESSAGE = "flagged";
  private static final String PUBLIC = "public";
  private static final String COUNT = "count";

  @Test
  void reportsProtectedMembersOfFinalClassesOnly() {
    final SyntaxNode finalClass = topLevel("final", classOf(member(method("m"), "protected")));
    final SyntaxNode openClass = topLevel(null, classOf(member(method("m"), "protected")));
    final SyntaxNode overridden =
        topLevel("final", classOf(member(method("m"), "protected", "@Override")));

    assertThat(messagesOf(new ProtectedInFinalCheck(MESSAGE), finalClass)).containsExactly(MESSAGE);
    assertThat(messagesOf(new ProtectedInFinalCheck(MESSAGE), openClass)).isEmpty();
    assertThat(messagesOf(new ProtectedInFinalCheck(MESSAGE), overridden)).isEmpty();
  }

  @Test
  void reportsPublicMembersOfNonPublicClassesOnly() {
    final SyntaxNode packagePrivate = topLevel(null, classOf(member(method("m"), PUBLIC)));
    final SyntaxNode publicClass = topLevel(PUBLIC, classOf(member(method("m"), PUBLIC)));
    final SyntaxNode noWrapper = compilationUnit(classOf(member(method("m"), PUBLIC)));

    assertThat(messagesOf(new PublicInNonPublicCheck(MESSAGE), packagePrivate))
        .containsExactly(MESSAGE);
    assertThat(messagesOf(new PublicInNonPublicCheck(MESSAGE), publicClass)).isEmpty();
    assertThat(messagesOf(new PublicInNonPublicCheck(MESSAGE), noWrapper)).isEmpty();
  }

  @Test
  void reportsPrivateFieldsThatNoMethodAssigns() {
    final SyntaxNode unassigned =
        compilationUnit(classOf(member(field(COUNT), "private"), member(method("read"))));
    final SyntaxNode assigned =
        compilationUnit(
            classOf(
                member(field(COUNT), "private"), member(method("set", assignment(COUNT, "1")))));
    final SyntaxNode finalField =
        compilationUnit(classOf(member(field(COUNT), "private", "final")));
    final SyntaxNode staticField =
        compilationUnit(classOf(member(field(COUNT), "private", "static")));
    final SyntaxNode publicField = compilationUnit(classOf(member(field(COUNT), PUBLIC)));

    assertThat(messagesOf(new ImmutableFieldCheck(MESSAGE), unassigned)).containsExactly(MESSAGE);
    assertThat(messagesOf(new ImmutableFieldCheck(MESSAGE), assigned)).isEmpty();
    assertThat(messagesOf(new ImmutableFieldCheck(MESSAGE), finalField)).isEmpty();
    assertThat(messagesOf(new ImmutableFieldCheck(MESSAGE), staticField)).isEmpty();
    assertThat(messagesOf(new ImmutableFieldCheck(MESSAGE), publicField)).isEmpty();
  }

  @Test
  void reportsPrivateFieldsWithOneUserThatAssignsIt() {
    final SyntaxNode singular =
        compilationUnit(
            classOf(
                member(field(COUNT), "private"), member(method("bump", assignment(COUNT, "1")))));
    final SyntaxNode sharedByTwo =
        compilationUnit(
            classOf(
                member(field(COUNT), "private"),
                member(method("read", n("identifier", t("IDENTIFIER", COUNT)))),
                member(method("bump", assignment(COUNT, "1")))));
    final SyntaxNode readOnly =
        compilationUnit(
            classOf(
                member(field(COUNT), "private"),
                member(method("read", n("identifier", t("IDENTIFIER", COUNT))))));

    assertThat(messagesOf(new SingularFieldCheck(MESSAGE), singular)).containsExactly(MESSAGE);
    assertThat(messagesOf(new SingularFieldCheck(MESSAGE), sharedByTwo)).isEmpty();
    assertThat(messagesOf(new SingularFieldCheck(MESSAGE), readOnly)).isEmpty();
  }

  @Test
  void reportsConcreteClassesOfStaticMembersWithoutMain() {
    final SyntaxNode utility = topLevel(null, classOf(member(method("helper"), PUBLIC, "static")));
    final SyntaxNode staticInitializer =
        topLevel(
            null,
            classOf(
                n("classBodyDeclaration", t("'static'", "static"), n("block")),
                member(method("helper"), PUBLIC, "static")));

    assertThat(messagesOf(new UtilityClassCheck(MESSAGE), utility)).containsExactly(MESSAGE);
    assertThat(messagesOf(new UtilityClassCheck(MESSAGE), staticInitializer))
        .containsExactly(MESSAGE);
  }

  @Test
  void skipsAbstractClassesSubclassesMainClassesAndInstanceMembers() {
    final SyntaxNode abstractClass =
        topLevel("abstract", classOf(member(method("helper"), PUBLIC, "static")));
    final SyntaxNode subclass =
        topLevel(
            null,
            classWith(
                List.of(t("'extends'", "extends")), member(method("helper"), PUBLIC, "static")));
    final SyntaxNode withMain = topLevel(null, classOf(member(method("main"), PUBLIC, "static")));
    final SyntaxNode instanceMember = topLevel(null, classOf(member(method("helper"), PUBLIC)));

    assertThat(messagesOf(new UtilityClassCheck(MESSAGE), abstractClass)).isEmpty();
    assertThat(messagesOf(new UtilityClassCheck(MESSAGE), subclass)).isEmpty();
    assertThat(messagesOf(new UtilityClassCheck(MESSAGE), withMain)).isEmpty();
    assertThat(messagesOf(new UtilityClassCheck(MESSAGE), instanceMember)).isEmpty();
  }

  @Test
  void reportsImportsFromConfiguredPackagesOutsideTheFile() {
    final SyntaxNode outside = compilationUnit(packageDecl("com.b"), importDecl("com.a.util.X"));
    final SyntaxNode inside =
        compilationUnit(packageDecl("com.a.service"), importDecl("com.a.util.X"));
    final SyntaxNode samePackage =
        compilationUnit(packageDecl("com.a"), importDecl("com.a.util.X"));
    final SyntaxNode unconfigured = compilationUnit(packageDecl("com.b"), importDecl("org.x.Y"));

    final PackageCouplingCheck check = new PackageCouplingCheck(List.of("com.a"), "{value}");

    assertThat(messagesOf(check, outside)).containsExactly("com.a.util.X");
    assertThat(messagesOf(check, inside)).isEmpty();
    assertThat(messagesOf(check, samePackage)).isEmpty();
    assertThat(messagesOf(check, unconfigured)).isEmpty();
  }

  @Test
  void reportsNothingWhenNoPackagesAreConfigured() {
    final SyntaxNode outside = compilationUnit(packageDecl("com.b"), importDecl("com.a.util.X"));

    assertThat(messagesOf(new PackageCouplingCheck(List.of(), "{value}"), outside)).isEmpty();
  }

  @Test
  void reportsThrowsOfTheCaughtTypeInsideTheTryBlock() {
    final SyntaxNode caught = compilationUnit(tryCatch("IOException", throwNew("IOException")));
    final SyntaxNode qualified =
        compilationUnit(tryCatch("java.io.IOException", throwNew("IOException")));
    final SyntaxNode otherType =
        compilationUnit(tryCatch("IOException", throwNew("IllegalStateException")));
    final SyntaxNode noCatch = compilationUnit(tryWithoutCatch(throwNew("IOException")));

    assertThat(messagesOf(new ExceptionFlowCheck(MESSAGE), caught)).containsExactly(MESSAGE);
    assertThat(messagesOf(new ExceptionFlowCheck(MESSAGE), qualified)).containsExactly(MESSAGE);
    assertThat(messagesOf(new ExceptionFlowCheck(MESSAGE), otherType)).isEmpty();
    assertThat(messagesOf(new ExceptionFlowCheck(MESSAGE), noCatch)).isEmpty();
  }
}
