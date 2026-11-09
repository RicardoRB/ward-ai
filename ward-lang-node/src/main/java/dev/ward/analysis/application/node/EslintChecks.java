package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.GetterReturnCheck;
import dev.ward.analysis.domain.rule.check.PreferRegexLiteralsCheck;
import dev.ward.analysis.domain.rule.check.UnsafeNegationCheck;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The TypeScript and JavaScript checks that only the ESLint core rules use (issue #326). */
final class EslintChecks {

  private static final String MESSAGE = "message";

  private EslintChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.of(
        "node-getter-return", EslintChecks::getterReturn,
        "node-unsafe-negation", EslintChecks::unsafeNegation,
        "node-prefer-regex-literals", EslintChecks::preferRegexLiterals);
  }

  private static GetterReturnCheck getterReturn(final TomlParseResult toml) {
    return new GetterReturnCheck(
        Boolean.TRUE.equals(toml.getBoolean("allow-implicit")), TomlValues.required(toml, MESSAGE));
  }

  private static UnsafeNegationCheck unsafeNegation(final TomlParseResult toml) {
    return new UnsafeNegationCheck(
        Boolean.TRUE.equals(toml.getBoolean("enforce-for-ordering-relations")),
        TomlValues.required(toml, MESSAGE));
  }

  private static PreferRegexLiteralsCheck preferRegexLiterals(final TomlParseResult toml) {
    return new PreferRegexLiteralsCheck(
        Boolean.TRUE.equals(toml.getBoolean("disallow-redundant-wrapping")),
        TomlValues.required(toml, MESSAGE));
  }
}
