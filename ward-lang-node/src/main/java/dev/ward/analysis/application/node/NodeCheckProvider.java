package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.node.NodeUnlistedImportCheck;
import dev.ward.analysis.domain.node.NodeUnresolvedImportCheck;
import java.util.Map;

/** The declarative checks that only TypeScript and JavaScript rules use, by name. */
public final class NodeCheckProvider implements CheckProvider {

  @Override
  public Map<String, CheckFactory> checks() {
    return Map.of(
        "unresolved-import",
        toml -> new NodeUnresolvedImportCheck(TomlValues.required(toml, "message")),
        "unlisted-import",
        toml -> new NodeUnlistedImportCheck(TomlValues.required(toml, "message")));
  }
}
