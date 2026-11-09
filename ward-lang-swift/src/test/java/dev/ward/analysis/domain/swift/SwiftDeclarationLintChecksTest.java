package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.check.RuleCheck;
import org.junit.jupiter.api.Test;

/** Lint checks of declarations and expressions, run over real Swift source. */
class SwiftDeclarationLintChecksTest {

  @Test
  void duplicateEnumCasesReportsEveryDeclarationOfTheSameName() {
    final RuleCheck check = new SwiftDuplicateEnumCasesCheck(LintRuns.MESSAGE);

    assertThat(
            LintRuns.lines(
                LintRuns.run(check, "enum E {\n case a(x: Int)\n case b\n case a(y: Int)\n}\n")))
        .containsExactly(2, LintRuns.FOURTH_LINE);
    assertThat(LintRuns.run(check, "enum A {\n case add\n}\nenum B {\n case add\n}\n")).isEmpty();
  }

  @Test
  void forWhereReportsALoopWhoseOnlyStatementIsASimpleIf() {
    final RuleCheck check = new SwiftForWhereCheck(LintRuns.MESSAGE);

    assertThat(LintRuns.lines(LintRuns.run(check, "for u in us {\n if u.id == 1 { return }\n}\n")))
        .containsExactly(2);
    assertThat(LintRuns.run(check, "for u in us {\n if u.id == 1 { }\n print(u)\n}\n")).isEmpty();
    assertThat(LintRuns.run(check, "for u in us {\n if u.id == 1 { } else { }\n}\n")).isEmpty();
    assertThat(LintRuns.run(check, "for u in us {\n if let id = u.id { }\n}\n")).isEmpty();
    assertThat(LintRuns.run(check, "for u in us {\n if u.id == 1 && u.age > 1 { }\n}\n")).isEmpty();
    assertThat(LintRuns.run(check, "for u in us where u.id == 1 { }\n")).isEmpty();
  }

  @Test
  void staticOverFinalClassReportsClassMembersOfFinalClassesAndFinalClassMembers() {
    final RuleCheck check = new SwiftStaticOverFinalClassCheck(LintRuns.MESSAGE);

    assertThat(LintRuns.lines(LintRuns.run(check, "final class C {\n class func f() {}\n}\n")))
        .containsExactly(2);
    assertThat(LintRuns.lines(LintRuns.run(check, "class C {\n final class func f() {}\n}\n")))
        .containsExactly(2);
    assertThat(LintRuns.run(check, "class C {\n class func f() {}\n static func g() {}\n}\n"))
        .isEmpty();
    assertThat(LintRuns.run(check, "final class C {\n static func f() {}\n}\n")).isEmpty();
  }

  @Test
  void duplicatedDictionaryKeyReportsEveryLaterRepeatOfASimpleKey() {
    final RuleCheck check = new SwiftDuplicatedDictionaryKeyCheck(LintRuns.MESSAGE);

    assertThat(LintRuns.lines(LintRuns.run(check, "let a = [1: \"1\", 2: \"2\", 1: \"one\"]\n")))
        .containsExactly(1);
    assertThat(LintRuns.run(check, "let a = [UUID(): \"1\", UUID(): \"2\"]\n")).isEmpty();
  }

  @Test
  void nsobjectPreferIsEqualReportsSelfEqualityOfObjectiveCClasses() {
    final RuleCheck check = new SwiftNsobjectPreferIsEqualCheck(LintRuns.MESSAGE);

    assertThat(
            LintRuns.lines(
                LintRuns.run(
                    check,
                    "class C: NSObject {\n static func == (lhs: C, rhs: C) -> Bool { true }\n}\n")))
        .containsExactly(2);
    assertThat(
            LintRuns.run(
                check, "class C {\n static func == (lhs: C, rhs: C) -> Bool { true }\n}\n"))
        .isEmpty();
  }

  @Test
  void redundantSendableReportsActorsAndMainActorTypes() {
    final RuleCheck check = new SwiftRedundantSendableCheck(LintRuns.MESSAGE);

    assertThat(LintRuns.lines(LintRuns.run(check, "actor A: Sendable {}\n"))).containsExactly(1);
    assertThat(LintRuns.lines(LintRuns.run(check, "@MainActor class C: Sendable {}\n")))
        .containsExactly(1);
    assertThat(LintRuns.run(check, "class C: Sendable {}\n")).isEmpty();
  }

  @Test
  void genericTypeNameAcceptsUppercaseAlphanumericNamesOfAtMostTwentyCharacters() {
    final RuleCheck check = new SwiftGenericTypeNameCheck(LintRuns.MESSAGE);

    assertThat(LintRuns.lines(LintRuns.run(check, "func foo<T_Foo>() {}\n"))).containsExactly(1);
    assertThat(LintRuns.lines(LintRuns.run(check, "func foo<type>() {}\n"))).containsExactly(1);
    assertThat(LintRuns.run(check, "func foo<Element>() {}\n")).isEmpty();
  }

  @Test
  void orphanedDocCommentReportsADocCommentFollowedByAPlainComment() {
    final RuleCheck check = new SwiftOrphanedDocCommentCheck(LintRuns.MESSAGE);

    assertThat(LintRuns.lines(LintRuns.run(check, "/// doc\n// plain\nvar x = 1\n")))
        .containsExactly(1);
    assertThat(LintRuns.run(check, "/// doc\nvar x = 1\n")).isEmpty();
    assertThat(LintRuns.run(check, "//// header\n/// doc\nvar x = 1\n")).isEmpty();
  }

  @Test
  void validIbinspectableReportsLetBindingsAndUnsupportedOrMissingTypes() {
    final RuleCheck check = new SwiftValidIbinspectableCheck(LintRuns.MESSAGE);

    assertThat(LintRuns.lines(LintRuns.run(check, "class F {\n @IBInspectable var x = 0\n}\n")))
        .containsExactly(2);
    assertThat(
            LintRuns.lines(
                LintRuns.run(check, "class F {\n @IBInspectable var x: UIEdgeInsets\n}\n")))
        .containsExactly(2);
    assertThat(LintRuns.run(check, "class F {\n @IBInspectable var x: String?\n}\n")).isEmpty();
    assertThat(LintRuns.run(check, "class F {\n var x = 0\n}\n")).isEmpty();
  }

  @Test
  void unneededOverrideReportsAFunctionThatOnlyForwardsToSuper() {
    final RuleCheck check = new SwiftUnneededOverrideCheck(LintRuns.MESSAGE);

    assertThat(
            LintRuns.lines(
                LintRuns.run(
                    check,
                    "class A: B {\n override func foo(a: Int) {\n super.foo(a: a)\n }\n}\n")))
        .containsExactly(2);
    assertThat(
            LintRuns.run(
                check, "class A: B {\n override func foo(a: Int) {\n super.foo(a: a + 1)\n }\n}\n"))
        .isEmpty();
    assertThat(
            LintRuns.run(
                check, "class A: B {\n override func foo(a: Int = 1) {\n super.foo(a: a)\n }\n}\n"))
        .isEmpty();
  }
}
