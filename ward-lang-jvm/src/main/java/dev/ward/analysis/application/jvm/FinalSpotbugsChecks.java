package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ClassShapeCheck;
import java.util.Locale;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** Builds the checks of the last SpotBugs patterns: class shape, resources and null handling. */
final class FinalSpotbugsChecks {

  private static final String MESSAGE = "message";

  private FinalSpotbugsChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(entry("class-shape", FinalSpotbugsChecks::classShape));
  }

  private static ClassShapeCheck classShape(final TomlParseResult toml) {
    return new ClassShapeCheck(
        ClassShapeCheck.Mode.valueOf(
            TomlValues.required(toml, "mode").replace('-', '_').toUpperCase(Locale.ROOT)),
        TomlValues.required(toml, MESSAGE));
  }
}
