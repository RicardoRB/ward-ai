package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.ConstructorsDeclarationGroupingCheck;
import dev.ward.analysis.domain.rule.check.OneStatementPerLineCheck;
import dev.ward.analysis.domain.rule.check.OverloadMethodsDeclarationOrderCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java coding checks that a rule body names, with their parameters. Each parameter is a
 * rule option, named after the Checkstyle property in kebab-case.
 */
final class CodingChecks {

  private static final String MESSAGE = "message";
  private static final String ORDER_BY_ARITY = "order-by-increasing-parameter-count";

  private static final Set<String> CHECKS =
      Set.of(
          "one-statement-per-line",
          "constructors-declaration-grouping",
          "overload-methods-declaration-order");

  private CodingChecks() {}

  /** Returns whether {@code name} is one of the Java coding checks. */
  static boolean isCodingCheck(final String name) {
    return CHECKS.contains(name);
  }

  /**
   * Builds the check named {@code name}.
   *
   * @throws dev.ward.analysis.domain.rule.RuleConfigurationException if a parameter is invalid
   */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "one-statement-per-line" ->
          new OneStatementPerLineCheck(
              flag(toml, "treat-try-resources-as-statement", false), message(toml));
      case "constructors-declaration-grouping" ->
          new ConstructorsDeclarationGroupingCheck(
              flag(toml, ORDER_BY_ARITY, false), message(toml));
      default ->
          new OverloadMethodsDeclarationOrderCheck(
              flag(toml, ORDER_BY_ARITY, false), message(toml));
    };
  }

  private static boolean flag(
      final TomlParseResult toml, final String key, final boolean fallback) {
    return Optional.ofNullable(toml.getBoolean(key)).orElse(fallback);
  }

  private static String message(final TomlParseResult toml) {
    return TomlValues.required(toml, MESSAGE);
  }
}
