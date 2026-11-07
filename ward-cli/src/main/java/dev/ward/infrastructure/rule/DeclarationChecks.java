package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.HiddenFieldCheck;
import dev.ward.analysis.domain.rule.check.ParameterAssignmentCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.VariableDeclarationUsageDistanceCheck;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java declaration checks that a rule body names, with their parameters. Each parameter
 * is a rule option, named after the Checkstyle property in kebab-case.
 */
final class DeclarationChecks {

  private static final String MESSAGE = "message";
  private static final int DEFAULT_DISTANCE = 3;

  private static final Set<String> CHECKS =
      Set.of("hidden-field", "parameter-assignment", "variable-declaration-usage-distance");

  private DeclarationChecks() {}

  /** Returns whether {@code name} is one of the Java declaration checks. */
  static boolean isDeclarationCheck(final String name) {
    return CHECKS.contains(name);
  }

  /**
   * Builds the check named {@code name}.
   *
   * @throws dev.ward.analysis.domain.rule.RuleConfigurationException if a parameter is invalid
   */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "hidden-field" -> hiddenField(toml);
      case "parameter-assignment" -> parameterAssignment(toml);
      default -> variableDeclarationUsageDistance(toml);
    };
  }

  private static RuleCheck variableDeclarationUsageDistance(final TomlParseResult toml) {
    return new VariableDeclarationUsageDistanceCheck(
        TomlValues.optionalInteger(toml, "allowed-distance", DEFAULT_DISTANCE),
        flag(toml, "ignore-finals", true),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck hiddenField(final TomlParseResult toml) {
    return new HiddenFieldCheck(
        flag(toml, "ignore-constructor-parameter", false),
        flag(toml, "ignore-setter", false),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck parameterAssignment(final TomlParseResult toml) {
    return new ParameterAssignmentCheck(TomlValues.required(toml, MESSAGE));
  }

  private static boolean flag(
      final TomlParseResult toml, final String key, final boolean fallback) {
    return Optional.ofNullable(toml.getBoolean(key)).orElse(fallback);
  }
}
