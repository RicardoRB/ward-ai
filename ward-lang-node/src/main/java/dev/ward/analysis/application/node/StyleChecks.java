package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.NodeArrowCallbackCheck;
import dev.ward.analysis.domain.rule.check.NodeConstructorGroupCheck;
import dev.ward.analysis.domain.rule.check.NodeElseAfterJumpCheck;
import dev.ward.analysis.domain.rule.check.NodeIllegalTokenCheck;
import dev.ward.analysis.domain.rule.check.NodeJavadocAlignCheck;
import dev.ward.analysis.domain.rule.check.NodeJavadocMethodCheck;
import dev.ward.analysis.domain.rule.check.NodeJavadocPositionCheck;
import dev.ward.analysis.domain.rule.check.NodeOverloadGroupingCheck;
import dev.ward.analysis.domain.rule.check.NodePreferConstCheck;
import dev.ward.analysis.domain.rule.check.NodeStringConcatCheck;
import dev.ward.analysis.domain.rule.check.NodeTypeOnlyImportCheck;
import dev.ward.analysis.domain.rule.check.NodeUselessConstructorCheck;
import dev.ward.analysis.domain.rule.check.VariableDistanceCheck;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The TypeScript and JavaScript checks that only the style (Checkstyle concepts, ESLint and Biome
 * style) rules use (issue #296).
 */
final class StyleChecks {

  private static final String MESSAGE = "message";

  private StyleChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Stream.concat(first().stream(), second().stream())
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static List<Map.Entry<String, CheckFactory>> first() {
    return List.of(
        Map.entry(
            "node-constructor-group",
            toml -> new NodeConstructorGroupCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-overload-grouping",
            toml -> new NodeOverloadGroupingCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-else-after-jump",
            toml ->
                new NodeElseAfterJumpCheck(
                    toml.getBoolean("allow-else-if", () -> true),
                    TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-illegal-token",
            toml ->
                new NodeIllegalTokenCheck(
                    TomlValues.strings(toml, "tokens"),
                    TomlValues.required(toml, "format"),
                    toml.getBoolean("ignore-case", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-javadoc-method",
            toml ->
                new NodeJavadocMethodCheck(
                    toml.getBoolean("allow-missing-param-tags", () -> false),
                    toml.getBoolean("allow-missing-return-tag", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-javadoc-position",
            toml -> new NodeJavadocPositionCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-javadoc-align",
            toml -> new NodeJavadocAlignCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static List<Map.Entry<String, CheckFactory>> second() {
    return List.of(
        Map.entry(
            "node-arrow-callback",
            toml ->
                new NodeArrowCallbackCheck(
                    toml.getBoolean("allow-named-functions", () -> false),
                    toml.getBoolean("allow-unbound-this", () -> true),
                    TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-string-concat",
            toml -> new NodeStringConcatCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-type-only-import",
            toml ->
                new NodeTypeOnlyImportCheck(
                    TomlValues.required(toml, MESSAGE),
                    TomlValues.required(toml, "partial-message"))),
        Map.entry(
            "node-useless-constructor",
            toml -> new NodeUselessConstructorCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-prefer-const",
            toml ->
                new NodePreferConstCheck(
                    "all".equals(toml.getString("destructuring")),
                    toml.getBoolean("ignore-read-before-assign", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-variable-distance",
            toml ->
                new VariableDistanceCheck(
                    TomlValues.integer(toml, "max"), TomlValues.required(toml, MESSAGE))));
  }
}
