package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.NodeArrowCallbackCheck;
import dev.ward.analysis.domain.rule.check.NodeConstructorGroupCheck;
import dev.ward.analysis.domain.rule.check.NodeElseAfterJumpCheck;
import dev.ward.analysis.domain.rule.check.NodeHiddenFieldCheck;
import dev.ward.analysis.domain.rule.check.NodeIllegalTokenCheck;
import dev.ward.analysis.domain.rule.check.NodeJavadocAlignCheck;
import dev.ward.analysis.domain.rule.check.NodeJavadocMethodCheck;
import dev.ward.analysis.domain.rule.check.NodeJavadocPositionCheck;
import dev.ward.analysis.domain.rule.check.NodeMagicNumberCheck;
import dev.ward.analysis.domain.rule.check.NodeMagicNumberFlags;
import dev.ward.analysis.domain.rule.check.NodeMissingJavadocTypeCheck;
import dev.ward.analysis.domain.rule.check.NodeOverloadGroupingCheck;
import dev.ward.analysis.domain.rule.check.NodePreferConstCheck;
import dev.ward.analysis.domain.rule.check.NodeStringConcatCheck;
import dev.ward.analysis.domain.rule.check.NodeTypeOnlyExportCheck;
import dev.ward.analysis.domain.rule.check.NodeTypeOnlyImportCheck;
import dev.ward.analysis.domain.rule.check.NodeUselessConstructorCheck;
import dev.ward.analysis.domain.rule.check.VariableDistanceCheck;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.tomlj.TomlParseResult;

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
            "node-hidden-field",
            toml ->
                new NodeHiddenFieldCheck(
                    toml.getBoolean("ignore-constructor-parameter", () -> false),
                    toml.getBoolean("ignore-setter", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-javadoc-method",
            toml ->
                new NodeJavadocMethodCheck(
                    toml.getBoolean("allow-missing-param-tags", () -> false),
                    toml.getBoolean("allow-missing-return-tag", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-missing-javadoc-type",
            toml ->
                new NodeMissingJavadocTypeCheck(
                    "public".equals(toml.getString("required")),
                    TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-javadoc-position",
            toml -> new NodeJavadocPositionCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-javadoc-align",
            toml -> new NodeJavadocAlignCheck(TomlValues.required(toml, MESSAGE))));
  }

  /** Reads a list of numbers; an entry that is not a decimal number is a configuration error. */
  static List<BigDecimal> numbers(final TomlParseResult toml, final String key) {
    return TomlValues.strings(toml, key).stream().map(entry -> number(key, entry)).toList();
  }

  private static BigDecimal number(final String key, final String entry) {
    try {
      return new BigDecimal(entry);
    } catch (NumberFormatException malformed) {
      throw new RuleConfigurationException(
          "'" + key + "' entry '" + entry + "' is not a number", malformed);
    }
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
            "node-type-only-export",
            toml ->
                new NodeTypeOnlyExportCheck(
                    TomlValues.required(toml, MESSAGE),
                    TomlValues.required(toml, "partial-message"))),
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
            "node-magic-number",
            toml ->
                new NodeMagicNumberCheck(
                    numbers(toml, "ignore-numbers"),
                    new NodeMagicNumberFlags(
                        toml.getBoolean("detect-objects", () -> false),
                        toml.getBoolean("ignore-array-indexes", () -> false),
                        toml.getBoolean("ignore-default-values", () -> false),
                        toml.getBoolean("ignore-class-field-initial-values", () -> false),
                        toml.getBoolean("enforce-const", () -> false)),
                    TomlValues.required(toml, MESSAGE),
                    TomlValues.required(toml, "const-message"))),
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
