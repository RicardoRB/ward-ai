package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.AbstractClassWithoutAbstractMethodCheck;
import dev.ward.analysis.domain.rule.check.ArrayIsStoredDirectlyCheck;
import dev.ward.analysis.domain.rule.check.AvoidReassigningCatchVariablesCheck;
import dev.ward.analysis.domain.rule.check.AvoidReassigningLoopVariablesCheck;
import dev.ward.analysis.domain.rule.check.CognitiveComplexityCheck;
import dev.ward.analysis.domain.rule.check.CognitiveTokens;
import dev.ward.analysis.domain.rule.check.CountedKinds;
import dev.ward.analysis.domain.rule.check.DeclarationFilter;
import dev.ward.analysis.domain.rule.check.DefaultLabelNotLastCheck;
import dev.ward.analysis.domain.rule.check.DescendantCountCheck;
import dev.ward.analysis.domain.rule.check.ForLoopCanBeForeachCheck;
import dev.ward.analysis.domain.rule.check.ImplicitFunctionalInterfaceCheck;
import dev.ward.analysis.domain.rule.check.LinesCheck;
import dev.ward.analysis.domain.rule.check.MethodReturnsInternalArrayCheck;
import dev.ward.analysis.domain.rule.check.PrecedingCommentCheck;
import dev.ward.analysis.domain.rule.check.PreserveStackTraceCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.TaintCheck;
import dev.ward.analysis.domain.rule.check.TextPatternCheck;
import dev.ward.analysis.domain.rule.check.TypeNameMismatchCheck;
import dev.ward.analysis.domain.rule.check.UnitTestAssertCountCheck;
import dev.ward.analysis.domain.rule.check.UnitTestLifecycleCheck;
import dev.ward.analysis.domain.rule.check.UnitTestMissingAnnotationCheck;
import dev.ward.analysis.domain.rule.check.UnitTestWithoutAssertCheck;
import dev.ward.analysis.domain.rule.check.UnnecessaryWarningSuppressionCheck;
import dev.ward.analysis.domain.rule.check.UnreferencedNameCheck;
import dev.ward.analysis.domain.rule.check.UnusedAssignmentCheck;
import dev.ward.analysis.domain.rule.check.UnusedFormalParameterCheck;
import dev.ward.analysis.domain.rule.check.UnusedLabelCheck;
import dev.ward.analysis.domain.rule.check.UnusedPrivateMethodCheck;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseResult;

/**
 * Builds the {@link RuleCheck} that a rule body describes. A body is TOML: {@code check} names one
 * check, and the other keys are that check's parameters. Unknown checks and missing or unusable
 * parameters are configuration errors.
 */
final class DeclarativeChecks {

  private static final String CHECK = "check";
  private static final String KINDS = "kinds";
  private static final String MAX = "max";
  private static final String MESSAGE = "message";
  private static final String MODIFIERS = "modifiers";
  private static final String REQUIRED = "required";
  private static final String EXCLUDED = "excluded";
  private static final String SPECS = "specs";
  private static final String COUNT_EMPTY = "count-empty";

  private DeclarativeChecks() {}

  /**
   * Builds the check of {@code body}.
   *
   * @throws RuleConfigurationException if the body is not valid TOML, names an unknown check, or
   *     has a missing or unusable parameter
   */
  static RuleCheck build(final String body) {
    return build(body, Set.of());
  }

  /**
   * Builds the check of {@code body} for a rule that covers {@code languages}. A generic check
   * reads its modifier names through the table of the language when the rule covers just one.
   *
   * @throws RuleConfigurationException as {@link #build(String)}
   */
  static RuleCheck build(final String body, final Set<AnalysisLanguage> languages) {
    final TomlParseResult toml = TomlValues.parse(body);
    final String name = TomlValues.required(toml, CHECK);
    try {
      final Optional<GenericKind> generic = GenericKind.select(name, toml);
      return generic.isPresent()
          ? GenericChecks.build(generic.get(), toml, ModifierTables.forLanguages(languages))
          : create(name, toml);
    } catch (final IllegalArgumentException exception) {
      throw new RuleConfigurationException(
          "check '" + name + "' cannot be used: " + exception.getMessage(), exception);
    } catch (final TomlInvalidTypeException exception) {
      throw new RuleConfigurationException(
          "check '" + name + "' has a parameter of the wrong type", exception);
    }
  }

  private static RuleCheck create(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "lines" ->
          new LinesCheck(
              TomlValues.strings(toml, KINDS),
              TomlValues.integer(toml, MAX),
              TomlValues.required(toml, MESSAGE),
              Optional.ofNullable(toml.getBoolean(COUNT_EMPTY)).orElse(true));
      case "descendant-count" -> descendantCount(toml);
      case "cognitive-complexity" -> cognitiveComplexity(toml);
      case "text-pattern" -> textPattern(toml);
      case "unreferenced-name" -> new UnreferencedNameCheck(TomlValues.required(toml, MESSAGE));
      default -> dataFlowCheck(name, toml);
    };
  }

  /** The checks that read data flow, and the remaining syntax checks. */
  private static RuleCheck dataFlowCheck(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "unused-assignment" -> new UnusedAssignmentCheck(TomlValues.required(toml, MESSAGE));
      case "taint" ->
          new TaintCheck(
              TaintSpecFiles.load(SpecPaths.of(toml, SPECS)), TomlValues.required(toml, MESSAGE));
      case "preceding-comment" -> precedingComment(toml);
      default -> labelOrJUnitCheck(name, toml);
    };
  }

  /** The checks over labels and JUnit test classes; other names go to the metric checks. */
  private static RuleCheck labelOrJUnitCheck(final String name, final TomlParseResult toml) {
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

  private static RuleCheck descendantCount(final TomlParseResult toml) {
    return new DescendantCountCheck(
        TomlValues.strings(toml, KINDS),
        countedKinds(toml, "counted"),
        TomlValues.optionalInteger(toml, "base", 0),
        TomlValues.integer(toml, MAX),
        TomlValues.required(toml, MESSAGE));
  }

  private static CountedKinds countedKinds(final TomlParseResult toml, final String key) {
    return new CountedKinds(
        TomlValues.strings(toml, key),
        TomlValues.kindMap(toml, "requires-sibling"),
        TomlValues.kindMap(toml, "cancelled-by"));
  }

  private static RuleCheck cognitiveComplexity(final TomlParseResult toml) {
    return new CognitiveComplexityCheck(
        TomlValues.strings(toml, KINDS),
        new CognitiveTokens(
            countedKinds(toml, "structures"),
            TomlValues.strings(toml, "flat"),
            TomlValues.optionalStrings(toml, "chaining")),
        TomlValues.integer(toml, MAX),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck textPattern(final TomlParseResult toml) {
    return new TextPatternCheck(
        filter(toml),
        TomlValues.required(toml, "name"),
        TomlValues.optionalStrings(toml, "skip"),
        TomlValues.required(toml, "pattern"),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck precedingComment(final TomlParseResult toml) {
    return new PrecedingCommentCheck(
        filter(toml),
        Optional.ofNullable(toml.getString("exempt-annotation")),
        TomlValues.required(toml, MESSAGE));
  }

  private static DeclarationFilter filter(final TomlParseResult toml) {
    return new DeclarationFilter(
        TomlValues.strings(toml, KINDS),
        TomlValues.strings(toml, MODIFIERS),
        Optional.ofNullable(toml.getString(REQUIRED)),
        Optional.ofNullable(toml.getString(EXCLUDED)));
  }
}
