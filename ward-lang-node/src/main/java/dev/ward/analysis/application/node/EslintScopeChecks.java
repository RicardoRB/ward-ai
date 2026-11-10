package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.NodeLabelVarCheck;
import dev.ward.analysis.domain.rule.check.NodeRedeclareCheck;
import dev.ward.analysis.domain.rule.check.NodeUnassignedVarsCheck;
import java.util.Map;

/** The ESLint core checks that read the scopes of the source (issue #326). */
final class EslintScopeChecks {

  private static final String MESSAGE = "message";

  private EslintScopeChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        Map.entry(
            "node-no-unassigned-vars",
            (CheckFactory) toml -> new NodeUnassignedVarsCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-no-redeclare",
            (CheckFactory) toml -> new NodeRedeclareCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-no-label-var",
            (CheckFactory) toml -> new NodeLabelVarCheck(TomlValues.required(toml, MESSAGE))));
  }
}
