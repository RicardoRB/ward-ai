package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.NodeUnlistedImportCheck;
import dev.ward.analysis.domain.rule.check.NodeUnresolvedImportCheck;
import java.util.Map;

/** The TypeScript and JavaScript checks that only the dead-code (knip) rules use (issue #331). */
final class DeadCodeChecks {

  private DeadCodeChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.of(
        "node-unresolved-import",
        toml -> new NodeUnresolvedImportCheck(TomlValues.required(toml, "message")),
        "node-unlisted-import",
        toml -> new NodeUnlistedImportCheck(TomlValues.required(toml, "message")));
  }
}
