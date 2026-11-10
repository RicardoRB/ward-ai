package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ArrayEqualsCheck;
import dev.ward.analysis.domain.rule.check.OverwrittenParameterCheck;
import dev.ward.analysis.domain.rule.check.RedundantInstanceofCheck;
import dev.ward.analysis.domain.rule.check.VarargPrimitiveArrayCheck;
import java.util.Locale;
import java.util.Map;

/**
 * The checks of the SpotBugs correctness rules over array types, instanceof tests and parameters.
 */
final class CorrectnessMoreChecks {

  private static final String MESSAGE = "message";

  private CorrectnessMoreChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "redundant-instanceof",
            toml -> new RedundantInstanceofCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "array-equals",
            toml ->
                new ArrayEqualsCheck(
                    ArrayEqualsCheck.Mismatch.valueOf(
                        TomlValues.required(toml, "mismatch")
                            .replace('-', '_')
                            .toUpperCase(Locale.ROOT)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "vararg-primitive-array",
            toml -> new VarargPrimitiveArrayCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "overwritten-parameter",
            toml -> new OverwrittenParameterCheck(TomlValues.required(toml, MESSAGE))));
  }
}
