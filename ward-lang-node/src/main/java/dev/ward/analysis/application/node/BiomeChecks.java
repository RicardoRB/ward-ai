package dev.ward.analysis.application.node;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.BiomeAutofocusCheck;
import dev.ward.analysis.domain.rule.check.BiomeBlankTargetCheck;
import dev.ward.analysis.domain.rule.check.BiomeConstantClampCheck;
import dev.ward.analysis.domain.rule.check.BiomeDuplicatePropsCheck;
import dev.ward.analysis.domain.rule.check.BiomeFinallyReturnCheck;
import dev.ward.analysis.domain.rule.check.BiomeJsxAttributeValueCheck;
import dev.ward.analysis.domain.rule.check.BiomeJsxEmptyElementCheck;
import dev.ward.analysis.domain.rule.check.BiomeJsxHandlerPairCheck;
import dev.ward.analysis.domain.rule.check.BiomeJsxRequiredAttributeCheck;
import dev.ward.analysis.domain.rule.check.BiomeMemberPropertyCheck;
import dev.ward.analysis.domain.rule.check.BiomeNamespaceAccessCheck;
import dev.ward.analysis.domain.rule.check.BiomeUnreachableSuperCheck;
import dev.ward.analysis.domain.rule.check.BiomeUnusedParameterCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/** The TypeScript and JavaScript checks that only the Biome lint rules use (issue #329). */
final class BiomeChecks {

  private static final String MESSAGE = "message";

  private BiomeChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "node-constant-clamp",
            toml -> new BiomeConstantClampCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-finally-return",
            toml -> new BiomeFinallyReturnCheck(TomlValues.required(toml, MESSAGE))),
        entry("node-member-property", BiomeChecks::memberProperty),
        entry(
            "node-namespace-access",
            toml -> new BiomeNamespaceAccessCheck(TomlValues.required(toml, MESSAGE))),
        entry("node-unused-parameter", BiomeChecks::unusedParameter),
        entry(
            "node-unreachable-super",
            toml -> new BiomeUnreachableSuperCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-autofocus", toml -> new BiomeAutofocusCheck(TomlValues.required(toml, MESSAGE))),
        entry("node-jsx-blank-target", BiomeChecks::blankTarget),
        entry("node-jsx-required-attribute", BiomeChecks::requiredAttribute),
        entry("node-jsx-handler-pair", BiomeChecks::handlerPair),
        entry(
            "node-jsx-empty-element",
            toml ->
                new BiomeJsxEmptyElementCheck(
                    TomlValues.optionalStrings(toml, "elements"),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "node-jsx-duplicate-props",
            toml -> new BiomeDuplicatePropsCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-jsx-attribute-value",
            toml ->
                new BiomeJsxAttributeValueCheck(
                    toml.getString("attribute"),
                    "expression".equals(toml.getString("at")),
                    Pattern.compile(TomlValues.required(toml, "text")),
                    TomlValues.required(toml, MESSAGE))));
  }

  private static RuleCheck handlerPair(final TomlParseResult toml) {
    return new BiomeJsxHandlerPairCheck(
        List.copyOf(TomlValues.optionalStrings(toml, "pairs")),
        List.copyOf(TomlValues.optionalStrings(toml, "exempt")),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck requiredAttribute(final TomlParseResult toml) {
    final String invalid = toml.getString("invalid");
    return new BiomeJsxRequiredAttributeCheck(
        TomlValues.optionalStrings(toml, "elements"),
        TomlValues.required(toml, "attribute"),
        new BiomeJsxRequiredAttributeCheck.ValueRule(
            TomlValues.optionalStrings(toml, "values"),
            invalid == null ? null : Pattern.compile(invalid),
            toml.getBoolean("silent-on-spread", () -> false)),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck blankTarget(final TomlParseResult toml) {
    return new BiomeBlankTargetCheck(
        TomlValues.optionalStrings(toml, "allow-domains"),
        toml.getBoolean("allow-no-referrer", () -> true),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck unusedParameter(final TomlParseResult toml) {
    return new BiomeUnusedParameterCheck(
        toml.getBoolean("ignore-rest-siblings", () -> true), TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck memberProperty(final TomlParseResult toml) {
    return new BiomeMemberPropertyCheck(
        TomlValues.strings(toml, "kinds"),
        Pattern.compile(TomlValues.required(toml, "text")),
        TomlValues.required(toml, MESSAGE));
  }
}
