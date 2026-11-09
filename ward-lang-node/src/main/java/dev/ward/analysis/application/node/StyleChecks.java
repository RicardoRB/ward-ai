package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.NodeArrowCallbackCheck;
import dev.ward.analysis.domain.rule.check.NodeConstructorGroupCheck;
import dev.ward.analysis.domain.rule.check.NodeElseAfterJumpCheck;
import dev.ward.analysis.domain.rule.check.NodeIllegalTokenCheck;
import dev.ward.analysis.domain.rule.check.NodeJavadocAlignCheck;
import dev.ward.analysis.domain.rule.check.NodeOverloadGroupingCheck;
import dev.ward.analysis.domain.rule.check.NodePreferConstCheck;
import dev.ward.analysis.domain.rule.check.NodeStringConcatCheck;
import dev.ward.analysis.domain.rule.check.NodeUselessConstructorCheck;
import dev.ward.analysis.domain.rule.check.VariableDistanceCheck;
import java.util.Map;

/**
 * The TypeScript and JavaScript checks that only the style (Checkstyle concepts, ESLint and Biome
 * style) rules use (issue #296).
 */
final class StyleChecks {

  private static final String MESSAGE = "message";

  private StyleChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.of(
        "node-constructor-group",
        toml -> new NodeConstructorGroupCheck(TomlValues.required(toml, MESSAGE)),
        "node-overload-grouping",
        toml -> new NodeOverloadGroupingCheck(TomlValues.required(toml, MESSAGE)),
        "node-else-after-jump",
        toml ->
            new NodeElseAfterJumpCheck(
                toml.getBoolean("allow-else-if", () -> true), TomlValues.required(toml, MESSAGE)),
        "node-illegal-token",
        toml ->
            new NodeIllegalTokenCheck(
                TomlValues.strings(toml, "tokens"),
                TomlValues.required(toml, "format"),
                toml.getBoolean("ignore-case", () -> false),
                TomlValues.required(toml, MESSAGE)),
        "node-javadoc-align",
        toml -> new NodeJavadocAlignCheck(TomlValues.required(toml, MESSAGE)),
        "node-arrow-callback",
        toml ->
            new NodeArrowCallbackCheck(
                toml.getBoolean("allow-named-functions", () -> false),
                toml.getBoolean("allow-unbound-this", () -> true),
                TomlValues.required(toml, MESSAGE)),
        "node-string-concat",
        toml -> new NodeStringConcatCheck(TomlValues.required(toml, MESSAGE)),
        "node-useless-constructor",
        toml -> new NodeUselessConstructorCheck(TomlValues.required(toml, MESSAGE)),
        "node-prefer-const",
        toml ->
            new NodePreferConstCheck(
                "all".equals(toml.getString("destructuring")),
                toml.getBoolean("ignore-read-before-assign", () -> false),
                TomlValues.required(toml, MESSAGE)),
        "node-variable-distance",
        toml ->
            new VariableDistanceCheck(
                TomlValues.integer(toml, "max"), TomlValues.required(toml, MESSAGE)));
  }
}
