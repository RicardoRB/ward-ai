package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.AbstractClassWithoutAbstractMethodCheck;
import dev.ward.analysis.domain.rule.check.ArrayIsStoredDirectlyCheck;
import dev.ward.analysis.domain.rule.check.AvoidReassigningCatchVariablesCheck;
import dev.ward.analysis.domain.rule.check.AvoidReassigningLoopVariablesCheck;
import dev.ward.analysis.domain.rule.check.DefaultLabelNotLastCheck;
import dev.ward.analysis.domain.rule.check.ForLoopCanBeForeachCheck;
import dev.ward.analysis.domain.rule.check.ImplicitFunctionalInterfaceCheck;
import dev.ward.analysis.domain.rule.check.MethodReturnsInternalArrayCheck;
import dev.ward.analysis.domain.rule.check.PreserveStackTraceCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.TypeNameMismatchCheck;
import dev.ward.analysis.domain.rule.check.UnitTestAssertCountCheck;
import dev.ward.analysis.domain.rule.check.UnitTestLifecycleCheck;
import dev.ward.analysis.domain.rule.check.UnitTestMissingAnnotationCheck;
import dev.ward.analysis.domain.rule.check.UnitTestWithoutAssertCheck;
import dev.ward.analysis.domain.rule.check.UnnecessaryWarningSuppressionCheck;
import dev.ward.analysis.domain.rule.check.UnusedFormalParameterCheck;
import dev.ward.analysis.domain.rule.check.UnusedLabelCheck;
import dev.ward.analysis.domain.rule.check.UnusedPrivateMethodCheck;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * Builds the checks of the rules that read labels, JUnit classes, declarations, assignments and
 * unused names. Other names are metric checks, which {@link MetricChecks} builds.
 */
final class ReviewChecks {

  private static final String MESSAGE = "message";
  private static final String MAX = "max";

  private ReviewChecks() {}

  /** The checks over labels, JUnit classes, declarations, assignments and unused names. */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "unused-label" -> new UnusedLabelCheck(TomlValues.required(toml, MESSAGE));
      case "unit-test-without-assert" ->
          new UnitTestWithoutAssertCheck(TomlValues.required(toml, MESSAGE));
      case "unit-test-assert-count" ->
          new UnitTestAssertCountCheck(
              TomlValues.integer(toml, MAX), TomlValues.required(toml, MESSAGE));
      case "unit-test-missing-annotation" ->
          new UnitTestMissingAnnotationCheck(
              Pattern.compile(TomlValues.required(toml, "class-pattern")),
              TomlValues.required(toml, MESSAGE));
      case "unit-test-lifecycle" ->
          new UnitTestLifecycleCheck(
              TomlValues.required(toml, "method"),
              TomlValues.strings(toml, "annotations"),
              TomlValues.required(toml, MESSAGE));
      default -> declarationCheck(name, toml);
    };
  }

  /** The checks over switch labels, abstract classes, interfaces and catch blocks. */
  private static RuleCheck declarationCheck(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "default-label-not-last" ->
          new DefaultLabelNotLastCheck(TomlValues.required(toml, MESSAGE));
      case "abstract-class-without-abstract-method" ->
          new AbstractClassWithoutAbstractMethodCheck(TomlValues.required(toml, MESSAGE));
      case "implicit-functional-interface" ->
          new ImplicitFunctionalInterfaceCheck(TomlValues.required(toml, MESSAGE));
      case "preserve-stack-trace" ->
          new PreserveStackTraceCheck(TomlValues.required(toml, MESSAGE));
      default -> flowCheck(name, toml);
    };
  }

  /** The checks over assignments, array fields and file names; other names go to the metrics. */
  private static RuleCheck flowCheck(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "avoid-reassigning-catch-variables" ->
          new AvoidReassigningCatchVariablesCheck(TomlValues.required(toml, MESSAGE));
      case "avoid-reassigning-loop-variables" ->
          new AvoidReassigningLoopVariablesCheck(
              TomlValues.required(toml, "for-reassign"),
              TomlValues.required(toml, "foreach-reassign"),
              TomlValues.required(toml, MESSAGE));
      case "method-returns-internal-array" ->
          new MethodReturnsInternalArrayCheck(TomlValues.required(toml, MESSAGE));
      case "array-is-stored-directly" ->
          new ArrayIsStoredDirectlyCheck(TomlValues.required(toml, MESSAGE));
      case "type-name-mismatch" -> new TypeNameMismatchCheck(TomlValues.required(toml, MESSAGE));
      default -> reviewCheck(name, toml);
    };
  }

  /** The checks over unused names, loops and suppressions; other names go to the metrics. */
  private static RuleCheck reviewCheck(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "unused-private-method" ->
          new UnusedPrivateMethodCheck(TomlValues.required(toml, MESSAGE));
      case "unused-formal-parameter" ->
          new UnusedFormalParameterCheck(
              toml.getBoolean("check-all", () -> false), TomlValues.required(toml, MESSAGE));
      case "for-loop-can-be-foreach" ->
          new ForLoopCanBeForeachCheck(TomlValues.required(toml, MESSAGE));
      case "unnecessary-warning-suppression" ->
          new UnnecessaryWarningSuppressionCheck(TomlValues.required(toml, MESSAGE));
      default -> MetricChecks.build(name, toml);
    };
  }
}
