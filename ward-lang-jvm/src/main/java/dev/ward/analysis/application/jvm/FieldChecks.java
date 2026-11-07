package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.FieldAccessCheck;
import dev.ward.analysis.domain.rule.check.NonTerminatingLoopCheck;
import dev.ward.analysis.domain.rule.check.SuperConstructorReadCheck;
import java.util.Locale;
import java.util.Map;

/** The checks of the rules that follow fields and the control flow of a body. */
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
                    TomlValues.required(toml, "message"))),
        entry(
            "super-constructor-read",
            toml -> new SuperConstructorReadCheck(TomlValues.required(toml, "message"))),
        entry(
            "non-terminating-loop",
            toml -> new NonTerminatingLoopCheck(TomlValues.required(toml, "message"))));
  }
}
