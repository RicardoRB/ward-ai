package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.GetterReturnCheck;
import dev.ward.analysis.domain.rule.check.NodeCatchParameterAssignCheck;
import dev.ward.analysis.domain.rule.check.NodeConstructorReturnCheck;
import dev.ward.analysis.domain.rule.check.NodeConstructorSuperCheck;
import dev.ward.analysis.domain.rule.check.NodeDupeClassMembersCheck;
import dev.ward.analysis.domain.rule.check.NodeDupeElseIfCheck;
import dev.ward.analysis.domain.rule.check.NodeIrregularWhitespaceCheck;
import dev.ward.analysis.domain.rule.check.NodeLossOfPrecisionCheck;
import dev.ward.analysis.domain.rule.check.NodeUnexpectedMultilineCheck;
import dev.ward.analysis.domain.rule.check.NodeUnusedExpressionsCheck;
import dev.ward.analysis.domain.rule.check.NodeUnusedPrivateMembersCheck;
import dev.ward.analysis.domain.rule.check.NodeUselessEscapeCheck;
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
    return Map.ofEntries(
        Map.entry("node-getter-return", EslintChecks::getterReturn),
        Map.entry("node-unsafe-negation", EslintChecks::unsafeNegation),
        Map.entry("node-prefer-regex-literals", EslintChecks::preferRegexLiterals),
        Map.entry("node-constructor-super", EslintChecks::constructorSuper),
        Map.entry("node-prefer-rest-params", EslintChecks::preferRestParams),
        Map.entry("node-no-setter-return", EslintChecks::setterReturn),
        Map.entry("node-string-escape", EslintChecks::stringEscape),
        Map.entry("node-catch-parameter-assign", EslintChecks::catchParameterAssign),
        Map.entry(
            "node-no-constructor-return",
            (CheckFactory)
                toml -> new NodeConstructorReturnCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-no-dupe-else-if",
            (CheckFactory) toml -> new NodeDupeElseIfCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry("node-no-irregular-whitespace", EslintChecks::irregularWhitespace),
        Map.entry("node-no-unused-expressions", EslintChecks::unusedExpressions),
        Map.entry(
            "node-no-loss-of-precision",
            (CheckFactory)
                toml -> new NodeLossOfPrecisionCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-no-unused-private-class-members",
            (CheckFactory) toml -> new NodeUnusedPrivateMembersCheck()),
        Map.entry(
            "node-no-unexpected-multiline",
            (CheckFactory) toml -> new NodeUnexpectedMultilineCheck()),
        Map.entry(
            "node-no-dupe-class-members",
            (CheckFactory)
                toml -> new NodeDupeClassMembersCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-no-useless-escape",
            (CheckFactory)
                toml ->
                    new NodeUselessEscapeCheck(
                        TomlValues.optionalStrings(toml, "allow-regex-characters"),
                        TomlValues.required(toml, MESSAGE))));
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

  private static NodeIrregularWhitespaceCheck irregularWhitespace(final TomlParseResult toml) {
    final NodeIrregularWhitespaceCheck.Skips skips =
        new NodeIrregularWhitespaceCheck.Skips(
            flag(toml, "skip-comments"),
            flag(toml, "skip-jsx-text"),
            flag(toml, "skip-reg-exps"),
            flag(toml, "skip-strings"),
            flag(toml, "skip-templates"));
    return new NodeIrregularWhitespaceCheck(skips, TomlValues.required(toml, MESSAGE));
  }

  private static NodeUnusedExpressionsCheck unusedExpressions(final TomlParseResult toml) {
    return new NodeUnusedExpressionsCheck(
        new NodeUnusedExpressionsCheck.Options(
            flag(toml, "allow-short-circuit"),
            flag(toml, "allow-ternary"),
            flag(toml, "allow-tagged-templates"),
            flag(toml, "enforce-for-jsx"),
            flag(toml, "ignore-directives")),
        TomlValues.required(toml, MESSAGE));
  }

  private static boolean flag(final TomlParseResult toml, final String key) {
    return Boolean.TRUE.equals(toml.getBoolean(key));
  }

  private static NodeCatchParameterAssignCheck catchParameterAssign(final TomlParseResult toml) {
    return new NodeCatchParameterAssignCheck(TomlValues.required(toml, MESSAGE));
  }
}
