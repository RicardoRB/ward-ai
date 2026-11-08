package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ClassShapeCheck;
import dev.ward.analysis.domain.rule.check.KnownNullCheck;
import dev.ward.analysis.domain.rule.check.NullableParameterCheck;
import dev.ward.analysis.domain.rule.check.SynchronizedNullCheckCheck;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** Builds the checks of the last SpotBugs patterns: class shape, resources and null handling. */
final class FinalSpotbugsChecks {

  private static final String MESSAGE = "message";

  private FinalSpotbugsChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("class-shape", FinalSpotbugsChecks::classShape),
        entry("known-null", FinalSpotbugsChecks::knownNull),
        entry(
            "synchronized-null-check",
            toml -> new SynchronizedNullCheckCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "nullable-parameter-dereferenced",
            toml -> new NullableParameterCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static KnownNullCheck knownNull(final TomlParseResult toml) {
    return new KnownNullCheck(
        KnownNullCheck.Mode.valueOf(
            TomlValues.required(toml, "mode").replace('-', '_').toUpperCase(Locale.ROOT)),
        TomlValues.required(toml, MESSAGE));
  }

  private static ClassShapeCheck classShape(final TomlParseResult toml) {
    return new ClassShapeCheck(
        ClassShapeCheck.Mode.valueOf(
            TomlValues.required(toml, "mode").replace('-', '_').toUpperCase(Locale.ROOT)),
        TomlValues.required(toml, MESSAGE));
  }
}
