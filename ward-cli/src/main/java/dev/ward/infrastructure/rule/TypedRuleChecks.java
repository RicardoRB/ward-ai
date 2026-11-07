package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.BitMaskComparisonCheck;
import dev.ward.analysis.domain.rule.check.CheckResultSetCheck;
import dev.ward.analysis.domain.rule.check.ConstantClampCheck;
import dev.ward.analysis.domain.rule.check.EnumComparisonCheck;
import dev.ward.analysis.domain.rule.check.ExhaustiveSwitchHasDefaultCheck;
import dev.ward.analysis.domain.rule.check.GuardLogStatementCheck;
import dev.ward.analysis.domain.rule.check.NonExhaustiveSwitchCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.UseEnumCollectionsCheck;
import org.tomlj.TomlParseResult;

/** Builds the checks that read the type facts of a file; other names are metric checks. */
final class TypedRuleChecks {

  private static final String MESSAGE = "message";

  private TypedRuleChecks() {}

  /** Builds the check of {@code name}, or the metric check when the name is not a typed one. */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "check-result-set" -> new CheckResultSetCheck(TomlValues.required(toml, MESSAGE));
      case "enum-comparison" -> new EnumComparisonCheck(TomlValues.required(toml, MESSAGE));
      case "use-enum-collections" ->
          new UseEnumCollectionsCheck(TomlValues.required(toml, MESSAGE));
      case "guard-log-statement" -> new GuardLogStatementCheck(TomlValues.required(toml, MESSAGE));
      case "bit-mask-comparison" -> new BitMaskComparisonCheck(TomlValues.required(toml, MESSAGE));
      case "constant-clamp" -> new ConstantClampCheck(TomlValues.required(toml, MESSAGE));
      case "exhaustive-switch-has-default" ->
          new ExhaustiveSwitchHasDefaultCheck(TomlValues.required(toml, MESSAGE));
      case "non-exhaustive-switch" ->
          new NonExhaustiveSwitchCheck(TomlValues.required(toml, MESSAGE));
      default -> MetricChecks.build(name, toml);
    };
  }
}
