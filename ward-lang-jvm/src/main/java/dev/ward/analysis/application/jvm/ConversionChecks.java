package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ImpossibleConversionCheck;
import dev.ward.analysis.domain.rule.check.ToArrayCastCheck;
import java.util.Locale;
import java.util.Map;

/** The checks of the rules about casts and instanceof tests that can never succeed. */
final class ConversionChecks {

  private static final String MESSAGE = "message";

  private ConversionChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "impossible-conversion",
            toml ->
                new ImpossibleConversionCheck(
                    ImpossibleConversionCheck.Mode.valueOf(
                        TomlValues.required(toml, "mode")
                            .replace('-', '_')
                            .toUpperCase(Locale.ROOT)),
                    TomlValues.required(toml, MESSAGE))),
        entry("to-array-cast", toml -> new ToArrayCastCheck(TomlValues.required(toml, MESSAGE))));
  }
}
