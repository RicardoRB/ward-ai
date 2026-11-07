package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.FieldAccessCheck;
import java.util.Locale;
import java.util.Map;

/** The checks of the rules that follow how the members of a class read and write its fields. */
final class FieldChecks {

  private FieldChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "field-access",
            toml ->
                new FieldAccessCheck(
                    FieldAccessCheck.Mode.valueOf(
                        TomlValues.required(toml, "mode")
                            .replace('-', '_')
                            .toUpperCase(Locale.ROOT)),
                    TomlValues.required(toml, "message"))));
  }
}
