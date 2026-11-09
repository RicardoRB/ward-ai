package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.GetterReturnCheck;
import dev.ward.analysis.domain.rule.check.NodeConstructorSuperCheck;
import dev.ward.analysis.domain.rule.check.PreferRegexLiteralsCheck;
import dev.ward.analysis.domain.rule.check.PreferRestParamsCheck;
import dev.ward.analysis.domain.rule.check.SetterReturnCheck;
import dev.ward.analysis.domain.rule.check.StringEscapeCheck;
import dev.ward.analysis.domain.rule.check.UnsafeNegationCheck;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The TypeScript and JavaScript checks that only the ESLint core rules use (issue #326). */
final class EslintChecks {

  private static final String MESSAGE = "message";
  private static final String DUPLICATE = "duplicate-message";

  private EslintChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.of(
        "node-getter-return", EslintChecks::getterReturn,
        "node-unsafe-negation", EslintChecks::unsafeNegation,
        "node-prefer-regex-literals", EslintChecks::preferRegexLiterals,
        "node-constructor-super", EslintChecks::constructorSuper,
        "node-prefer-rest-params", EslintChecks::preferRestParams,
        "node-no-setter-return", EslintChecks::setterReturn,
        "node-string-escape", EslintChecks::stringEscape);
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

  private static NodeConstructorSuperCheck constructorSuper(final TomlParseResult toml) {
    return new NodeConstructorSuperCheck(
        TomlValues.required(toml, MESSAGE), TomlValues.required(toml, DUPLICATE));
  }

  private static PreferRestParamsCheck preferRestParams(final TomlParseResult toml) {
    return new PreferRestParamsCheck(TomlValues.required(toml, MESSAGE));
  }

  private static SetterReturnCheck setterReturn(final TomlParseResult toml) {
    return new SetterReturnCheck(TomlValues.required(toml, MESSAGE));
  }

  private static StringEscapeCheck stringEscape(final TomlParseResult toml) {
    return new StringEscapeCheck(
        Boolean.TRUE.equals(toml.getBoolean("decimal")), TomlValues.required(toml, MESSAGE));
  }
}
