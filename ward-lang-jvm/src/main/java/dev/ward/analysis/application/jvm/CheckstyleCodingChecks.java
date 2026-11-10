package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlLists;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ArrayTrailingCommaCheck;
import dev.ward.analysis.domain.rule.check.ConstructorsDeclarationGroupingCheck;
import dev.ward.analysis.domain.rule.check.ExplicitInitializationCheck;
import dev.ward.analysis.domain.rule.check.MagicNumberCheck;
import dev.ward.analysis.domain.rule.check.NoEnumTrailingCommaCheck;
import dev.ward.analysis.domain.rule.check.OneStatementPerLineCheck;
import dev.ward.analysis.domain.rule.check.OverloadMethodsDeclarationOrderCheck;
import dev.ward.analysis.domain.rule.check.TextBlockFormatCheck;
import java.util.List;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * Builds the checks of the Checkstyle coding rules that name Java-only structure: statements per
 * line, declaration grouping, trailing commas, explicit defaults and magic numbers. Each parameter
 * is a rule option, named after the Checkstyle property in kebab-case.
 */
final class CheckstyleCodingChecks {

  private static final String MESSAGE = "message";
  private static final String ORDER_BY_ARITY = "order-by-increasing-parameter-count";

  private CheckstyleCodingChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(statements(), declarations(), literals()));
  }

  private static Map<String, CheckFactory> statements() {
    return Map.ofEntries(
        entry(
            "one-statement-per-line",
            toml ->
                new OneStatementPerLineCheck(
                    flag(toml, "treat-try-resources-as-statement"),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "no-enum-trailing-comma",
            toml -> new NoEnumTrailingCommaCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "text-block-google-style-formatting",
            toml -> new TextBlockFormatCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static Map<String, CheckFactory> declarations() {
    return Map.ofEntries(
        entry(
            "constructors-declaration-grouping",
            toml ->
                new ConstructorsDeclarationGroupingCheck(
                    flag(toml, ORDER_BY_ARITY), TomlValues.required(toml, MESSAGE))),
        entry(
            "overload-methods-declaration-order",
            toml ->
                new OverloadMethodsDeclarationOrderCheck(
                    flag(toml, ORDER_BY_ARITY), TomlValues.required(toml, MESSAGE))),
        entry(
            "explicit-initialization",
            toml ->
                new ExplicitInitializationCheck(
                    flag(toml, "only-object-references"), TomlValues.required(toml, MESSAGE))),
        entry(
            "array-trailing-comma",
            toml ->
                new ArrayTrailingCommaCheck(
                    flag(toml, "always-demand-trailing-comma"),
                    TomlValues.required(toml, MESSAGE))));
  }

  private static Map<String, CheckFactory> literals() {
    return Map.ofEntries(
        entry(
            "magic-number",
            toml ->
                new MagicNumberCheck(
                    TomlLists.optional(toml, "ignore-numbers"),
                    flag(toml, "ignore-field-declaration"),
                    flag(toml, "ignore-annotation"),
                    flag(toml, "ignore-hash-code-method"),
                    TomlValues.required(toml, MESSAGE))));
  }

  private static boolean flag(final TomlParseResult toml, final String key) {
    return toml.getBoolean(key, () -> false);
  }
}
