package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.AnonymousLinesCheck;
import dev.ward.analysis.domain.rule.check.BooleanOperatorCheck;
import dev.ward.analysis.domain.rule.check.ClassCouplingCheck;
import dev.ward.analysis.domain.rule.check.LineLengthCheck;
import dev.ward.analysis.domain.rule.check.MethodCountCheck;
import dev.ward.analysis.domain.rule.check.NPathCheck;
import dev.ward.analysis.domain.rule.check.NcssCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import org.tomlj.TomlParseResult;

/** Builds the Java size and metric checks that a rule body names, with their parameters. */
final class MetricChecks {

  private static final String MAX = "max";
  private static final String MESSAGE = "message";
  private static final String TAB_WIDTH = "tab-width";
  private static final String IGNORE_PATTERN = "ignore-pattern";
  private static final String METHOD_MAXIMUM = "method-maximum";
  private static final String CLASS_MAXIMUM = "class-maximum";
  private static final String FILE_MAXIMUM = "file-maximum";
  private static final String MAX_TOTAL = "max-total";
  private static final String MAX_PRIVATE = "max-private";
  private static final String MAX_PACKAGE = "max-package";
  private static final String MAX_PROTECTED = "max-protected";
  private static final String MAX_PUBLIC = "max-public";
  private static final String TYPES = "types";
  private static final String EXCLUDED_CLASSES = "excluded-classes";

  private MetricChecks() {}

  /**
   * Builds the check named {@code name}.
   *
   * @throws RuleConfigurationException if the name is unknown
   */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "line-length" ->
          new LineLengthCheck(
              TomlValues.integer(toml, MAX),
              TomlValues.integer(toml, TAB_WIDTH),
              TomlValues.required(toml, IGNORE_PATTERN),
              TomlValues.required(toml, MESSAGE));
      case "boolean-operators" ->
          new BooleanOperatorCheck(
              TomlValues.integer(toml, MAX), TomlValues.required(toml, MESSAGE));
      case "npath" ->
          new NPathCheck(TomlValues.integer(toml, MAX), TomlValues.required(toml, MESSAGE));
      case "ncss" ->
          new NcssCheck(
              TomlValues.integer(toml, METHOD_MAXIMUM),
              TomlValues.integer(toml, CLASS_MAXIMUM),
              TomlValues.integer(toml, FILE_MAXIMUM),
              TomlValues.required(toml, MESSAGE));
      case "method-count" ->
          new MethodCountCheck(
              new MethodCountCheck.Limits(
                  TomlValues.integer(toml, MAX_TOTAL),
                  TomlValues.integer(toml, MAX_PRIVATE),
                  TomlValues.integer(toml, MAX_PACKAGE),
                  TomlValues.integer(toml, MAX_PROTECTED),
                  TomlValues.integer(toml, MAX_PUBLIC)),
              TomlValues.required(toml, MESSAGE));
      case "class-coupling" ->
          new ClassCouplingCheck(
              TomlValues.strings(toml, TYPES),
              TomlValues.optionalStrings(toml, EXCLUDED_CLASSES),
              TomlValues.integer(toml, MAX),
              TomlValues.required(toml, MESSAGE));
      case "anonymous-lines" ->
          new AnonymousLinesCheck(
              TomlValues.integer(toml, MAX), TomlValues.required(toml, MESSAGE));
      default -> throw new RuleConfigurationException("unknown check '" + name + "'");
    };
  }

}
