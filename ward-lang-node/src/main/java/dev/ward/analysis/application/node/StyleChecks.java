package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.NodeConstructorGroupCheck;
import dev.ward.analysis.domain.rule.check.NodeElseAfterJumpCheck;
import dev.ward.analysis.domain.rule.check.NodeOverloadGroupingCheck;
import dev.ward.analysis.domain.rule.check.NodePreferConstCheck;
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
        "node-prefer-const",
        toml -> new NodePreferConstCheck(TomlValues.required(toml, MESSAGE)),
        "node-variable-distance",
        toml ->
            new VariableDistanceCheck(
                TomlValues.integer(toml, "max"), TomlValues.required(toml, MESSAGE)));
  }
}
