package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.CovariantEqualsCheck;
import dev.ward.analysis.domain.rule.check.EqualsAvoidNullCheck;
import dev.ward.analysis.domain.rule.check.EqualsHashCodeCheck;
import dev.ward.analysis.domain.rule.check.FallThroughCheck;
import dev.ward.analysis.domain.rule.check.IllegalCatchCheck;
import dev.ward.analysis.domain.rule.check.IllegalThrowsCheck;
import dev.ward.analysis.domain.rule.check.MissingSwitchDefaultCheck;
import dev.ward.analysis.domain.rule.check.ReturnCountCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Set;
import org.tomlj.TomlParseResult;

/**
 * Builds the checks of the Checkstyle-style coding rules: switch structure, return statements,
 * equals and hashCode, and the exception types that catch and throws clauses name.
 */
final class CodingChecks {

  private static final String MESSAGE = "message";
  private static final String MAX = "max";
  private static final Set<String> NAMES =
      Set.of(
          "fall-through",
          "missing-switch-default",
          "return-count",
          "equals-hash-code",
          "covariant-equals",
          "equals-avoid-null",
          "illegal-catch",
          "illegal-throws");

  private CodingChecks() {}

  /** Tells whether {@code name} is one of the coding checks. */
  static boolean isCoding(final String name) {
    return NAMES.contains(name);
  }

  /**
   * Builds the coding check {@code name}.
   *
   * @throws IllegalArgumentException if {@code name} is not a coding check
   */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "fall-through" ->
          new FallThroughCheck(
              toml.getBoolean("check-last-group", () -> false), TomlValues.required(toml, MESSAGE));
      case "missing-switch-default" ->
          new MissingSwitchDefaultCheck(TomlValues.required(toml, MESSAGE));
      case "return-count" ->
          new ReturnCountCheck(
              TomlValues.integer(toml, MAX),
              TomlValues.integer(toml, "max-for-void"),
              TomlValues.required(toml, MESSAGE));
      default -> buildEquality(name, toml);
    };
  }

  private static RuleCheck buildEquality(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "equals-hash-code" -> new EqualsHashCodeCheck(TomlValues.required(toml, MESSAGE));
      case "covariant-equals" -> new CovariantEqualsCheck(TomlValues.required(toml, MESSAGE));
      case "equals-avoid-null" ->
          new EqualsAvoidNullCheck(
              toml.getBoolean("ignore-equals-ignore-case", () -> false),
              TomlValues.required(toml, MESSAGE));
      default -> buildException(name, toml);
    };
  }

  private static RuleCheck buildException(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "illegal-catch" ->
          new IllegalCatchCheck(
              TomlValues.strings(toml, "illegal-classes"), TomlValues.required(toml, MESSAGE));
      case "illegal-throws" ->
          new IllegalThrowsCheck(
              TomlValues.strings(toml, "illegal-classes"),
              TomlValues.strings(toml, "ignored-method-names"),
              toml.getBoolean("ignore-overridden-methods", () -> true),
              TomlValues.required(toml, MESSAGE));
      default -> throw new IllegalArgumentException("not a coding check: " + name);
    };
  }
}
