package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.check.RuleCheck;
import org.junit.jupiter.api.Test;

/** Lint checks of declarations and expressions, run over real Swift source. */
class SwiftStatementLintChecksTest {

  @Test
  void duplicateConditionsReportsRepeatedLinksAndCases() {
    final RuleCheck check = new SwiftDuplicateConditionsCheck(LintRuns.MESSAGE);

    assertThat(
            LintRuns.lines(
                LintRuns.run(check, "if a < 5 {\n} else if b {\n} else if a < 5 {\n}\n")))
        .containsExactly(1, LintRuns.THIRD_LINE);
    assertThat(LintRuns.run(check, "if a < 5, b {\n} else if a < 5 {\n}\n")).isEmpty();
    assertThat(LintRuns.lines(LintRuns.run(check, "switch x {\ncase 1:\n y()\ncase 1:\n z()\n}\n")))
        .containsExactly(2, LintRuns.FOURTH_LINE);
  }

  @Test
  void duplicateImportsReportsRepeatsAndSubmodulesOfImportedModules() {
    final RuleCheck check = new SwiftDuplicateImportsCheck(LintRuns.MESSAGE);

    assertThat(LintRuns.lines(LintRuns.run(check, "import Foo\nimport Foo\n"))).containsExactly(2);
    assertThat(LintRuns.lines(LintRuns.run(check, "import Foo\nimport Foo.Bar\n")))
        .containsExactly(2);
    assertThat(LintRuns.run(check, "import Foo\n@testable import Foo.Bar\n")).isEmpty();
  }

  @Test
  void blanketDisableCommandReportsRulesDisabledForTheRestOfTheFile() {
    final RuleCheck check = new SwiftBlanketDisableCommandCheck(LintRuns.MESSAGE);

    assertThat(LintRuns.lines(LintRuns.run(check, "// swiftlint:disable unused_import\n")))
        .containsExactly(1);
    assertThat(
            LintRuns.run(
                check, "// swiftlint:disable unused_import\n// swiftlint:enable unused_import\n"))
        .isEmpty();
    assertThat(LintRuns.lines(LintRuns.run(check, "// swiftlint:enable unused_import\n")))
        .containsExactly(1);
    assertThat(LintRuns.run(check, "// swiftlint:disable:next unused_import\n")).isEmpty();
  }

  @Test
  void deploymentTargetReportsAvailabilityAtOrBelowTheTargetOnly() {
    final RuleCheck check =
        new SwiftDeploymentTargetCheck(LintRuns.MESSAGE, java.util.Map.of("iOS", "13.0"));

    assertThat(LintRuns.lines(LintRuns.run(check, "if #available(iOS 12, *) {\n}\n")))
        .containsExactly(1);
    assertThat(LintRuns.lines(LintRuns.run(check, "@available(iOS 13.0, *)\nfunc f() {}\n")))
        .containsExactly(1);
    assertThat(LintRuns.run(check, "if #available(iOS 14, *) {\n}\n")).isEmpty();
    assertThat(LintRuns.run(check, "if #available(macOS 10, *) {\n}\n")).isEmpty();
    assertThat(SwiftDeploymentTargetCheck.compare("13", "13.0")).isZero();
  }

  @Test
  void voidFunctionInTernaryReportsDiscardedCallsButNotValues() {
    final RuleCheck check = new SwiftVoidFunctionInTernaryCheck(LintRuns.MESSAGE);

    assertThat(
            LintRuns.lines(
                LintRuns.run(check, "func run() {\n flag ? doA() : doB()\n done()\n}\n")))
        .containsExactly(2);
    assertThat(LintRuns.run(check, "func run() -> Int {\n return flag ? doA() : doB()\n}\n"))
        .isEmpty();
    assertThat(LintRuns.run(check, "var a = b ? c() : d()\n")).isEmpty();
  }

  @Test
  void unneededSynthesizedInitializerReportsAnInitializerThatAssignsEveryProperty() {
    final RuleCheck check = new SwiftUnneededSynthesizedInitializerCheck(LintRuns.MESSAGE);

    assertThat(
            LintRuns.lines(
                LintRuns.run(
                    check, "struct S {\n let a: Int\n init(a: Int) {\n self.a = a\n }\n}\n")))
        .containsExactly(LintRuns.THIRD_LINE);
    assertThat(
            LintRuns.run(check, "struct S {\n let a: Int\n init(b: Int) {\n self.a = b\n }\n}\n"))
        .isEmpty();
  }

  @Test
  void unusedSetterValueReportsASetterThatNeverReadsItsValue() {
    final RuleCheck check = new SwiftUnusedSetterValueCheck(LintRuns.MESSAGE);

    assertThat(
            LintRuns.lines(
                LintRuns.run(
                    check, "var v: Int {\n get {\n return 1\n }\n set {\n x = 2\n }\n}\n")))
        .containsExactly(LintRuns.FIFTH_LINE);
    assertThat(
            LintRuns.run(
                check, "var v: Int {\n get {\n return 1\n }\n set {\n x = newValue\n }\n}\n"))
        .isEmpty();
  }

  @Test
  void captureVariableReportsAGlobalVarInACaptureList() {
    final RuleCheck check = new SwiftCaptureVariableCheck(LintRuns.MESSAGE);

    assertThat(
            LintRuns.lines(
                LintRuns.run(check, "var j = 0\nlet c: () -> Void = { [j] in print(j) }\n")))
        .containsExactly(2);
    assertThat(LintRuns.run(check, "let k = 0\nlet c: () -> Void = { [k] in print(k) }\n"))
        .isEmpty();
    assertThat(
            LintRuns.run(
                check,
                "func f() {\n var local = 0\n let c: () -> Void = { [local] in print(local) }\n}\n"))
        .isEmpty();
  }

  @Test
  void typesafeArrayInitReportsAnIdentityMapOfASequence() {
    final RuleCheck check = new SwiftTypesafeArrayInitCheck(LintRuns.MESSAGE);

    assertThat(LintRuns.lines(LintRuns.run(check, "let a = [1, 2].map { $0 }\n")))
        .containsExactly(1);
    assertThat(LintRuns.run(check, "let a = [1, 2].map { $0 + 1 }\n")).isEmpty();
    assertThat(LintRuns.run(check, "struct S {}\nlet b = S().map { $0 }\n")).isEmpty();
  }
}
