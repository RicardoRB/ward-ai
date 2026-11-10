package dev.ward.analysis.application.node;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.BiomeConfusingVoidTypeCheck;
import dev.ward.analysis.domain.rule.check.BiomeImplicitAnyLetCheck;
import dev.ward.analysis.domain.rule.check.BiomeMisleadingInstantiatorCheck;
import dev.ward.analysis.domain.rule.check.BiomeVoidTypeReturnCheck;
import java.util.Map;

/** The TypeScript type checks of the Biome lint rules, which read declarations and void types. */
final class BiomeTypeChecks {

  private static final String MESSAGE = "message";

  private BiomeTypeChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "node-implicit-any-let",
            toml -> new BiomeImplicitAnyLetCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-misleading-instantiator",
            toml -> new BiomeMisleadingInstantiatorCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-confusing-void-type",
            toml -> new BiomeConfusingVoidTypeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-void-type-return",
            toml -> new BiomeVoidTypeReturnCheck(TomlValues.required(toml, MESSAGE))));
  }
}
