package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.FloatPrecisionCheck;
import dev.ward.analysis.domain.rule.check.LibraryCallCheck;
import dev.ward.analysis.domain.rule.check.NonNullFieldInitCheck;
import dev.ward.analysis.domain.rule.check.NullDataflowCheck;
import dev.ward.analysis.domain.rule.check.UncallableAnonymousMethodCheck;
import java.util.Locale;
import java.util.Map;

/** The checks of the library call, single precision and null data-flow rules of Java. */
final class FinalChecks {

  private static final String MESSAGE = "message";
  private static final String MODE = "mode";

  private FinalChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "library-call",
            toml ->
                new LibraryCallCheck(
                    LibraryCallCheck.Mode.valueOf(mode(toml)), TomlValues.required(toml, MESSAGE))),
        entry(
            "float-precision", toml -> new FloatPrecisionCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "uncallable-anonymous-method",
            toml -> new UncallableAnonymousMethodCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "null-dataflow",
            toml ->
                new NullDataflowCheck(
                    NullDataflowCheck.Mode.valueOf(mode(toml)),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "nonnull-field-init",
            toml -> new NonNullFieldInitCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static String mode(final org.tomlj.TomlParseResult toml) {
    return TomlValues.required(toml, MODE).replace('-', '_').toUpperCase(Locale.ROOT);
  }
}
