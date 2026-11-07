package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AbstractClassWithoutAbstractMethodCheck;
import dev.ward.analysis.domain.rule.check.ArrayIsStoredDirectlyCheck;
import dev.ward.analysis.domain.rule.check.AvoidReassigningCatchVariablesCheck;
import dev.ward.analysis.domain.rule.check.AvoidReassigningLoopVariablesCheck;
import dev.ward.analysis.domain.rule.check.BitMaskComparisonCheck;
import dev.ward.analysis.domain.rule.check.ConstantClampCheck;
import dev.ward.analysis.domain.rule.check.DefaultLabelNotLastCheck;
import dev.ward.analysis.domain.rule.check.EqualsOnUnrelatedTypesCheck;
import dev.ward.analysis.domain.rule.check.ForLoopCanBeForeachCheck;
import dev.ward.analysis.domain.rule.check.ImplicitFunctionalInterfaceCheck;
import dev.ward.analysis.domain.rule.check.MethodReturnsInternalArrayCheck;
import dev.ward.analysis.domain.rule.check.PreserveStackTraceCheck;
import dev.ward.analysis.domain.rule.check.TypeNameMismatchCheck;
import dev.ward.analysis.domain.rule.check.UnitTestAssertCountCheck;
import dev.ward.analysis.domain.rule.check.UnitTestLifecycleCheck;
import dev.ward.analysis.domain.rule.check.UnitTestMissingAnnotationCheck;
import dev.ward.analysis.domain.rule.check.UnitTestWithoutAssertCheck;
import dev.ward.analysis.domain.rule.check.UnnecessaryWarningSuppressionCheck;
import dev.ward.analysis.domain.rule.check.UnusedFormalParameterCheck;
import dev.ward.analysis.domain.rule.check.UnusedLabelCheck;
import dev.ward.analysis.domain.rule.check.UnusedPrivateMethodCheck;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds the checks of the rules that read labels, JUnit classes, declarations, assignments and
 * unused names. Other names are metric checks, which {@link MetricChecks} builds.
 */
final class ReviewChecks {

  private static final String MESSAGE = "message";
  private static final String MAX = "max";

  private ReviewChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(build(), declarationCheck(), flowCheck(), reviewCheck()));
  }

  /** The checks over labels, JUnit classes, declarations, assignments and unused names. */
  private static Map<String, CheckFactory> build() {
    return Map.ofEntries(
        entry("unused-label", toml -> new UnusedLabelCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unit-test-without-assert",
            toml -> new UnitTestWithoutAssertCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unit-test-assert-count",
            toml ->
                new UnitTestAssertCountCheck(
                    TomlValues.integer(toml, MAX), TomlValues.required(toml, MESSAGE))),
        entry(
            "unit-test-missing-annotation",
            toml ->
                new UnitTestMissingAnnotationCheck(
                    Pattern.compile(TomlValues.required(toml, "class-pattern")),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "unit-test-lifecycle",
            toml ->
                new UnitTestLifecycleCheck(
                    TomlValues.required(toml, "method"),
                    TomlValues.strings(toml, "annotations"),
                    TomlValues.required(toml, MESSAGE))));
  }

  /** The checks over switch labels, abstract classes, interfaces and catch blocks. */
  private static Map<String, CheckFactory> declarationCheck() {
    return Map.ofEntries(
        entry(
            "default-label-not-last",
            toml -> new DefaultLabelNotLastCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "abstract-class-without-abstract-method",
            toml ->
                new AbstractClassWithoutAbstractMethodCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "implicit-functional-interface",
            toml -> new ImplicitFunctionalInterfaceCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "preserve-stack-trace",
            toml -> new PreserveStackTraceCheck(TomlValues.required(toml, MESSAGE))));
  }

  /** The checks over assignments, array fields and file names; other names go to the metrics. */
  private static Map<String, CheckFactory> flowCheck() {
    return Map.ofEntries(
        entry(
            "avoid-reassigning-catch-variables",
            toml -> new AvoidReassigningCatchVariablesCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "avoid-reassigning-loop-variables",
            toml ->
                new AvoidReassigningLoopVariablesCheck(
                    TomlValues.required(toml, "for-reassign"),
                    TomlValues.required(toml, "foreach-reassign"),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "method-returns-internal-array",
            toml -> new MethodReturnsInternalArrayCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "array-is-stored-directly",
            toml -> new ArrayIsStoredDirectlyCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "type-name-mismatch",
            toml -> new TypeNameMismatchCheck(TomlValues.required(toml, MESSAGE))));
  }

  /** The checks over unused names, loops and suppressions; other names go to the metrics. */
  private static Map<String, CheckFactory> reviewCheck() {
    return Map.ofEntries(
        entry(
            "unused-private-method",
            toml -> new UnusedPrivateMethodCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "equals-on-unrelated-types",
            toml -> new EqualsOnUnrelatedTypesCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unused-formal-parameter",
            toml ->
                new UnusedFormalParameterCheck(
                    toml.getBoolean("check-all", () -> false), TomlValues.required(toml, MESSAGE))),
        entry(
            "for-loop-can-be-foreach",
            toml -> new ForLoopCanBeForeachCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unnecessary-warning-suppression",
            toml -> new UnnecessaryWarningSuppressionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "bit-mask-comparison",
            toml -> new BitMaskComparisonCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "constant-clamp", toml -> new ConstantClampCheck(TomlValues.required(toml, MESSAGE))));
  }
}
