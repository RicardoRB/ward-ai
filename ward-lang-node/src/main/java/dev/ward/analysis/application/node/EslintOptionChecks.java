package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.NodeInnerDeclarationsCheck;
import dev.ward.analysis.domain.rule.check.NodeIrregularWhitespaceCheck;
import dev.ward.analysis.domain.rule.check.NodePromiseRejectCheck;
import dev.ward.analysis.domain.rule.check.NodeUnusedExpressionsCheck;
import org.tomlj.TomlParseResult;

/** The factories of the ESLint core checks that read options from the rule's table (issue #326). */
final class EslintOptionChecks {

  private static final String MESSAGE = "message";

  private EslintOptionChecks() {}

  static NodeIrregularWhitespaceCheck irregularWhitespace(final TomlParseResult toml) {
    final NodeIrregularWhitespaceCheck.Skips skips =
        new NodeIrregularWhitespaceCheck.Skips(
            flag(toml, "skip-comments"),
            flag(toml, "skip-jsx-text"),
            flag(toml, "skip-reg-exps"),
            flag(toml, "skip-strings"),
            flag(toml, "skip-templates"));
    return new NodeIrregularWhitespaceCheck(skips, TomlValues.required(toml, MESSAGE));
  }

  static NodeUnusedExpressionsCheck unusedExpressions(final TomlParseResult toml) {
    return new NodeUnusedExpressionsCheck(
        new NodeUnusedExpressionsCheck.Options(
            flag(toml, "allow-short-circuit"),
            flag(toml, "allow-ternary"),
            flag(toml, "allow-tagged-templates"),
            flag(toml, "enforce-for-jsx"),
            flag(toml, "ignore-directives")),
        TomlValues.required(toml, MESSAGE));
  }

  static NodeInnerDeclarationsCheck innerDeclarations(final TomlParseResult toml) {
    return new NodeInnerDeclarationsCheck(
        "both".equals(toml.getString("declarations")),
        "allow".equals(toml.getString("block-scoped-functions")),
        TomlValues.required(toml, MESSAGE));
  }

  static NodePromiseRejectCheck promiseReject(final TomlParseResult toml) {
    return new NodePromiseRejectCheck(
        flag(toml, "allow-empty-reject"), TomlValues.required(toml, MESSAGE));
  }

  static boolean flag(final TomlParseResult toml, final String key) {
    return Boolean.TRUE.equals(toml.getBoolean(key));
  }
}
