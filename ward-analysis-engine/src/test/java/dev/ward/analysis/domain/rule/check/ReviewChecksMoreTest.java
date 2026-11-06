package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReviewChecksMoreTest extends TreeBuilders {

  @Test
  void storingAnArrayParameterInAFieldIsReported() {
    final SyntaxNode stored =
        unit(
            Declarations.classWith(
                Declarations.intField("values"),
                Declarations.constructorStoring("values", "input", "input")));
    final SyntaxNode copied =
        unit(
            Declarations.classWith(
                Declarations.intField("values"),
                Declarations.constructorStoring("values", "input", "input.clone()")));
    assertThat(run(new ArrayIsStoredDirectlyCheck(MESSAGE), stored)).hasSize(1);
    assertThat(run(new ArrayIsStoredDirectlyCheck(MESSAGE), copied)).isEmpty();
  }

  @Test
  void aTypeNamedOtherThanItsFileIsReported() {
    final SyntaxNode holder = unit(MethodTrees.typeNamed("Holder"));
    final SyntaxNode matching = unit(MethodTrees.typeNamed("violating"));
    assertThat(
            new TypeNameMismatchCheck(MESSAGE).check(holder, new SourceFile("violating.java", "")))
        .hasSize(1);
    assertThat(
            new TypeNameMismatchCheck(MESSAGE)
                .check(matching, new SourceFile("violating.java", "")))
        .isEmpty();
    assertThat(new TypeNameMismatchCheck(MESSAGE).check(holder, new SourceFile("", ""))).isEmpty();
  }

  @Test
  void anUnusedPrivateMethodIsReportedUntilItIsCalled() {
    final SyntaxNode unused = unit(Declarations.classWith(Declarations.privateMethod("unused")));
    final SyntaxNode called =
        unit(
            Declarations.classWith(
                Declarations.privateMethod("used"),
                n(
                    STATEMENT,
                    n(
                        METHOD_CALL,
                        id("used"),
                        n("arguments", t(TOKEN_OPEN, "("), t(TOKEN_CLOSE, ")"))))));
    assertThat(run(new UnusedPrivateMethodCheck(MESSAGE), unused)).hasSize(1);
    assertThat(run(new UnusedPrivateMethodCheck(MESSAGE), called)).isEmpty();
  }

  @Test
  void anUnusedParameterIsReportedInPrivateMethodsOrWithCheckAll() {
    final SyntaxNode privateUnused =
        unit(Declarations.classWith(MethodTrees.privateMethodWithCount()));
    final SyntaxNode publicUnused =
        unit(Declarations.classWith(MethodTrees.publicMethodWithCount()));
    assertThat(run(new UnusedFormalParameterCheck(false, MESSAGE), privateUnused)).hasSize(1);
    assertThat(run(new UnusedFormalParameterCheck(false, MESSAGE), publicUnused)).isEmpty();
    assertThat(run(new UnusedFormalParameterCheck(true, MESSAGE), publicUnused)).hasSize(1);
  }

  @Test
  void aCountingLoopThatOnlyReadsElementsIsReported() {
    final SyntaxNode reads = unit(MethodTrees.countingLoop("values[i];"));
    final SyntaxNode scales = unit(MethodTrees.countingLoop("values[i] * i;"));
    assertThat(run(new ForLoopCanBeForeachCheck(MESSAGE), reads)).hasSize(1);
    assertThat(run(new ForLoopCanBeForeachCheck(MESSAGE), scales)).isEmpty();
  }

  @Test
  void everySuppressionIsReported() {
    final SyntaxNode annotation =
        unit(n("classOrInterfaceModifier", n(ANNOTATION, t("'@'", "@"), id("SuppressWarnings"))));
    final SyntaxNode comment = unit(t("comment", "// NOPMD"));
    final SyntaxNode none = unit(t("comment", "// plain"));
    assertThat(run(new UnnecessaryWarningSuppressionCheck(MESSAGE), annotation)).hasSize(1);
    assertThat(run(new UnnecessaryWarningSuppressionCheck(MESSAGE), comment)).hasSize(1);
    assertThat(run(new UnnecessaryWarningSuppressionCheck(MESSAGE), none)).isEmpty();
  }

  @Test
  void junitMethodsNeedAssertionsAndTheRightCount() {
    final SyntaxNode without = unit(MethodTrees.testClass(List.of(MethodTrees.testMethod(0))));
    final SyntaxNode two = unit(MethodTrees.testClass(List.of(MethodTrees.testMethod(2))));
    assertThat(run(new UnitTestWithoutAssertCheck(MESSAGE), without)).hasSize(1);
    assertThat(run(new UnitTestWithoutAssertCheck(MESSAGE), two)).isEmpty();
    assertThat(run(new UnitTestAssertCountCheck(1, MESSAGE), two)).hasSize(1);
    assertThat(run(new UnitTestAssertCountCheck(2, MESSAGE), two)).isEmpty();
  }

  @Test
  void aTestNamedClassNeedsTheTestAnnotation() {
    final SyntaxNode bare =
        unit(MethodTrees.namedClass("CounterTest", List.of(MethodTrees.publicTestMethod(false))));
    final SyntaxNode annotated =
        unit(MethodTrees.namedClass("CounterTest", List.of(MethodTrees.publicTestMethod(true))));
    final SyntaxNode other =
        unit(MethodTrees.namedClass("Helpers", List.of(MethodTrees.publicTestMethod(false))));
    assertThat(run(new UnitTestMissingAnnotationCheck(Pattern.compile("Test"), MESSAGE), bare))
        .hasSize(1);
    assertThat(run(new UnitTestMissingAnnotationCheck(Pattern.compile("Test"), MESSAGE), annotated))
        .isEmpty();
    assertThat(run(new UnitTestMissingAnnotationCheck(Pattern.compile("Test"), MESSAGE), other))
        .isEmpty();
  }

  @Test
  void aLifecycleMethodNeedsItsAnnotationInATestClass() {
    final SyntaxNode bare =
        unit(
            MethodTrees.testClass(
                List.of(MethodTrees.testMethod(1), MethodTrees.lifecycle("tearDown", false))));
    final SyntaxNode annotated =
        unit(
            MethodTrees.testClass(
                List.of(MethodTrees.testMethod(1), MethodTrees.lifecycle("tearDown", true))));
    final SyntaxNode noTests =
        unit(MethodTrees.testClass(List.of(MethodTrees.lifecycle("tearDown", false))));
    assertThat(run(new UnitTestLifecycleCheck("tearDown", Set.of("AfterEach"), MESSAGE), bare))
        .hasSize(1);
    assertThat(run(new UnitTestLifecycleCheck("tearDown", Set.of("AfterEach"), MESSAGE), annotated))
        .isEmpty();
    assertThat(run(new UnitTestLifecycleCheck("tearDown", Set.of("AfterEach"), MESSAGE), noTests))
        .isEmpty();
  }

  private static List<RuleMatch> run(final UnitCheck check, final SyntaxNode root) {
    return check.check(root, FILE);
  }
}
