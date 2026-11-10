package dev.ward.analysis.application.node;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.BiomeConfusingLabelsCheck;
import java.util.Map;

/** The statement checks of the Biome lint rules, which read labels and their bodies. */
final class BiomeStatementChecks {

  private static final String MESSAGE = "message";

  private BiomeStatementChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "node-confusing-labels",
            toml ->
                new BiomeConfusingLabelsCheck(
                    TomlValues.strings(toml, "allowed-labels"),
                    TomlValues.required(toml, MESSAGE))));
  }
}
