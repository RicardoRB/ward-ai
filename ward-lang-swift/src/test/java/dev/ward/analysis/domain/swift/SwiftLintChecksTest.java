package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the lint checks over real Swift source, so every position is the parser's own. */
class SwiftLintChecksTest {

  private static final String MESSAGE = "finding";

  private static List<RuleMatch> run(final RuleCheck check, final String source) {
    final SyntaxNode tree =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("x.swift", AnalysisLanguage.SWIFT, source))
            .tree();
    return check.matches(tree);
  }

  private static List<Integer> lines(final List<RuleMatch> found) {
    return found.stream().map(match -> match.span().startLine()).toList();
  }

  @Test
  void duplicateEnumCasesReportsEveryDeclarationOfTheSameName() {
    final RuleCheck check = new SwiftDuplicateEnumCasesCheck(MESSAGE);

    assertThat(lines(run(check, "enum E {\n case a(x: Int)\n case b\n case a(y: Int)\n}\n")))
        .containsExactly(2, 4);
    assertThat(run(check, "enum A {\n case add\n}\nenum B {\n case add\n}\n")).isEmpty();
  }

  @Test
  void forWhereReportsALoopWhoseOnlyStatementIsASimpleIf() {
    final RuleCheck check = new SwiftForWhereCheck(MESSAGE);

    assertThat(lines(run(check, "for u in us {\n if u.id == 1 { return }\n}\n"))).containsExactly(2);
    assertThat(run(check, "for u in us {\n if u.id == 1 { }\n print(u)\n}\n")).isEmpty();
    assertThat(run(check, "for u in us {\n if u.id == 1 { } else { }\n}\n")).isEmpty();
    assertThat(run(check, "for u in us {\n if let id = u.id { }\n}\n")).isEmpty();
    assertThat(run(check, "for u in us {\n if u.id == 1 && u.age > 1 { }\n}\n")).isEmpty();
    assertThat(run(check, "for u in us where u.id == 1 { }\n")).isEmpty();
  }

  @Test
  void staticOverFinalClassReportsClassMembersOfFinalClassesAndFinalClassMembers() {
    final RuleCheck check = new SwiftStaticOverFinalClassCheck(MESSAGE);

    assertThat(lines(run(check, "final class C {\n class func f() {}\n}\n"))).containsExactly(2);
    assertThat(lines(run(check, "class C {\n final class func f() {}\n}\n"))).containsExactly(2);
    assertThat(run(check, "class C {\n class func f() {}\n static func g() {}\n}\n")).isEmpty();
    assertThat(run(check, "final class C {\n static func f() {}\n}\n")).isEmpty();
  }

  @Test
  void duplicatedDictionaryKeyReportsEveryLaterRepeatOfASimpleKey() {
    final RuleCheck check = new SwiftDuplicatedDictionaryKeyCheck(MESSAGE);

    assertThat(lines(run(check, "let a = [1: \"1\", 2: \"2\", 1: \"one\"]\n"))).containsExactly(1);
    assertThat(run(check, "let a = [UUID(): \"1\", UUID(): \"2\"]\n")).isEmpty();
  }

  @Test
  void nsobjectPreferIsEqualReportsSelfEqualityOfObjectiveCClasses() {
    final RuleCheck check = new SwiftNsobjectPreferIsEqualCheck(MESSAGE);

    assertThat(lines(run(check, "class C: NSObject {\n static func == (lhs: C, rhs: C) -> Bool { true }\n}\n")))
        .containsExactly(2);
    assertThat(run(check, "class C {\n static func == (lhs: C, rhs: C) -> Bool { true }\n}\n")).isEmpty();
  }

  @Test
  void redundantSendableReportsActorsAndMainActorTypes() {
    final RuleCheck check = new SwiftRedundantSendableCheck(MESSAGE);

    assertThat(lines(run(check, "actor A: Sendable {}\n"))).containsExactly(1);
    assertThat(lines(run(check, "@MainActor class C: Sendable {}\n"))).containsExactly(1);
    assertThat(run(check, "class C: Sendable {}\n")).isEmpty();
  }

  @Test
  void genericTypeNameAcceptsUppercaseAlphanumericNamesOfAtMostTwentyCharacters() {
    final RuleCheck check = new SwiftGenericTypeNameCheck(MESSAGE);

    assertThat(lines(run(check, "func foo<T_Foo>() {}\n"))).containsExactly(1);
    assertThat(lines(run(check, "func foo<type>() {}\n"))).containsExactly(1);
    assertThat(run(check, "func foo<Element>() {}\n")).isEmpty();
  }

  @Test
  void orphanedDocCommentReportsADocCommentFollowedByAPlainComment() {
    final RuleCheck check = new SwiftOrphanedDocCommentCheck(MESSAGE);

    assertThat(lines(run(check, "/// doc\n// plain\nvar x = 1\n"))).containsExactly(1);
    assertThat(run(check, "/// doc\nvar x = 1\n")).isEmpty();
    assertThat(run(check, "//// header\n/// doc\nvar x = 1\n")).isEmpty();
  }

  @Test
  void validIbinspectableReportsLetBindingsAndUnsupportedOrMissingTypes() {
    final RuleCheck check = new SwiftValidIbinspectableCheck(MESSAGE);

    assertThat(lines(run(check, "class F {\n @IBInspectable var x = 0\n}\n"))).containsExactly(2);
    assertThat(lines(run(check, "class F {\n @IBInspectable var x: UIEdgeInsets\n}\n"))).containsExactly(2);
    assertThat(run(check, "class F {\n @IBInspectable var x: String?\n}\n")).isEmpty();
    assertThat(run(check, "class F {\n var x = 0\n}\n")).isEmpty();
  }

  @Test
  void unneededOverrideReportsAFunctionThatOnlyForwardsToSuper() {
    final RuleCheck check = new SwiftUnneededOverrideCheck(MESSAGE);

    assertThat(lines(run(check, "class A: B {\n override func foo(a: Int) {\n super.foo(a: a)\n }\n}\n")))
        .containsExactly(2);
    assertThat(run(check, "class A: B {\n override func foo(a: Int) {\n super.foo(a: a + 1)\n }\n}\n"))
        .isEmpty();
    assertThat(run(check, "class A: B {\n override func foo(a: Int = 1) {\n super.foo(a: a)\n }\n}\n"))
        .isEmpty();
  }

  @Test
  void duplicateConditionsReportsRepeatedLinksAndCases() {
    final RuleCheck check = new SwiftDuplicateConditionsCheck(MESSAGE);

    assertThat(lines(run(check, "if a < 5 {\n} else if b {\n} else if a < 5 {\n}\n")))
        .containsExactly(1, 3);
    assertThat(run(check, "if a < 5, b {\n} else if a < 5 {\n}\n")).isEmpty();
    assertThat(lines(run(check, "switch x {\ncase 1:\n y()\ncase 1:\n z()\n}\n"))).containsExactly(2, 4);
  }

  @Test
  void duplicateImportsReportsRepeatsAndSubmodulesOfImportedModules() {
    final RuleCheck check = new SwiftDuplicateImportsCheck(MESSAGE);

    assertThat(lines(run(check, "import Foo\nimport Foo\n"))).containsExactly(2);
    assertThat(lines(run(check, "import Foo\nimport Foo.Bar\n"))).containsExactly(2);
    assertThat(run(check, "import Foo\n@testable import Foo.Bar\n")).isEmpty();
  }

  @Test
  void blanketDisableCommandReportsRulesDisabledForTheRestOfTheFile() {
    final RuleCheck check = new SwiftBlanketDisableCommandCheck(MESSAGE);

    assertThat(lines(run(check, "// swiftlint:disable unused_import\n"))).containsExactly(1);
    assertThat(run(check, "// swiftlint:disable unused_import\n// swiftlint:enable unused_import\n")).isEmpty();
    assertThat(lines(run(check, "// swiftlint:enable unused_import\n"))).containsExactly(1);
    assertThat(run(check, "// swiftlint:disable:next unused_import\n")).isEmpty();
  }
}
