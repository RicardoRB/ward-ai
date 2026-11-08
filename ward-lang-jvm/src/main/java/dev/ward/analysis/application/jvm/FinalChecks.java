package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.FloatPrecisionCheck;
import dev.ward.analysis.domain.rule.check.LibraryCallCheck;
import java.util.Locale;
import java.util.Map;

/** The checks of the library call, single precision and null data-flow rules of Java. */
final class FinalChecks {

  private static final String MESSAGE = "message";

  private FinalChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "library-call",
            toml ->
                new LibraryCallCheck(
                    LibraryCallCheck.Mode.valueOf(
                        TomlValues.required(toml, "mode")
                            .replace('-', '_')
                            .toUpperCase(Locale.ROOT)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "float-precision",
            toml -> new FloatPrecisionCheck(TomlValues.required(toml, MESSAGE))));
  }
}
