package dev.ward.analysis.application.node;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.node.UnusedLabelCheck;
import java.util.Map;

/** The declarative checks that only TypeScript and JavaScript rules use. */
public final class NodeCheckProvider implements CheckProvider {

  @Override
  public Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "unused-node-label",
            toml -> new UnusedLabelCheck(TomlValues.required(toml, "message"))));
  }
}
