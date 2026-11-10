package dev.ward.analysis.application.node;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.BiomeActivedescendantTabindexCheck;
import dev.ward.analysis.domain.rule.check.BiomeAriaPropsCheck;
import dev.ward.analysis.domain.rule.check.BiomeAriaPropsForRoleCheck;
import dev.ward.analysis.domain.rule.check.BiomeAriaPropsSupportedByRoleCheck;
import dev.ward.analysis.domain.rule.check.BiomeAriaRoleCheck;
import dev.ward.analysis.domain.rule.check.BiomeAriaUnsupportedElementsCheck;
import dev.ward.analysis.domain.rule.check.BiomeAriaValuesCheck;
import dev.ward.analysis.domain.rule.check.BiomeInteractiveToNoninteractiveRoleCheck;
import dev.ward.analysis.domain.rule.check.BiomeNoRedundantRolesCheck;
import dev.ward.analysis.domain.rule.check.BiomeNoninteractiveElementInteractionsCheck;
import dev.ward.analysis.domain.rule.check.BiomeNoninteractiveTabindexCheck;
import dev.ward.analysis.domain.rule.check.BiomeNoninteractiveToInteractiveRoleCheck;
import dev.ward.analysis.domain.rule.check.BiomeSemanticElementCheck;
import dev.ward.analysis.domain.rule.check.BiomeStaticElementInteractionsCheck;
import dev.ward.analysis.domain.rule.check.BiomeSvgTitleCheck;
import java.util.HashMap;
import java.util.Map;

/** The checks that read Biome 2.5.15's mirrored ARIA tables, kept apart from the JSX checks. */
final class AriaChecks {

  private static final String MESSAGE = "message";

  private AriaChecks() {}

  /** The checks that read the mirrored ARIA tables of Biome 2.5.15. */
  static Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> checks = new HashMap<>(elementChecks());
    checks.putAll(tableChecks());
    checks.putAll(interactionChecks());
    return Map.copyOf(checks);
  }

  /** The checks that read the ARIA role, attribute and value tables. */
  private static Map<String, CheckFactory> tableChecks() {
    return Map.ofEntries(
        entry(
            "node-aria-props-for-role",
            toml ->
                new BiomeAriaPropsForRoleCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-aria-props-supported-by-role",
            toml ->
                new BiomeAriaPropsSupportedByRoleCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-aria-unsupported-elements",
            toml ->
                new BiomeAriaUnsupportedElementsCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-activedescendant-tabindex",
            toml ->
                new BiomeActivedescendantTabindexCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-aria-props",
            toml -> new BiomeAriaPropsCheck(AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-aria-role",
            toml -> new BiomeAriaRoleCheck(AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-aria-values",
            toml ->
                new BiomeAriaValuesCheck(AriaTables.load(), TomlValues.required(toml, MESSAGE))));
  }

  /** The checks that read the element roles and the interactions they allow. */
  private static Map<String, CheckFactory> interactionChecks() {
    return Map.ofEntries(
        entry(
            "node-noninteractive-tabindex",
            toml ->
                new BiomeNoninteractiveTabindexCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-noninteractive-to-interactive-role",
            toml ->
                new BiomeNoninteractiveToInteractiveRoleCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-interactive-to-noninteractive-role",
            toml ->
                new BiomeInteractiveToNoninteractiveRoleCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-noninteractive-element-interactions",
            toml ->
                new BiomeNoninteractiveElementInteractionsCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-static-element-interactions",
            toml ->
                new BiomeStaticElementInteractionsCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-no-redundant-roles",
            toml ->
                new BiomeNoRedundantRolesCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))));
  }

  /** The semantic-element and svg-title checks, which read the element concepts. */
  private static Map<String, CheckFactory> elementChecks() {
    return Map.ofEntries(
        entry(
            "node-semantic-element",
            toml ->
                new BiomeSemanticElementCheck(
                    AriaTables.load(), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-svg-title",
            toml -> new BiomeSvgTitleCheck(AriaTables.load(), TomlValues.required(toml, MESSAGE))));
  }
}
