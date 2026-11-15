package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ArrayLastParameterCheck;
import dev.ward.analysis.domain.rule.check.UnnecessaryVarargsArrayCheck;
import java.util.Map;

/** Builds the checks of the rules about array and varargs parameters. */
final class VarargsChecks {

  private VarargsChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "array-last-parameter",
            toml -> new ArrayLastParameterCheck(TomlValues.required(toml, "message"))),
        entry(
            "unnecessary-varargs-array",
            toml -> new UnnecessaryVarargsArrayCheck(TomlValues.required(toml, "message"))));
  }
}
