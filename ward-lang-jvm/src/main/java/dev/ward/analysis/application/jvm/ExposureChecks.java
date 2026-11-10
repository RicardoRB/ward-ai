package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.BadSerializableFieldCheck;
import dev.ward.analysis.domain.rule.check.ExposedStateCheck;
import dev.ward.analysis.domain.rule.check.InnerClassUseCheck;
import dev.ward.analysis.domain.rule.check.InnerSerializableCheck;
import dev.ward.analysis.domain.rule.check.NonSerializableValueCheck;
import dev.ward.analysis.domain.rule.check.ServletFieldCheck;
import dev.ward.analysis.domain.rule.check.StaticFieldWritesCheck;
import dev.ward.analysis.domain.rule.check.TransientFieldCheck;
import java.util.Map;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/** Builds the checks of the SpotBugs exposure, serialization, servlet and inner-class patterns. */
final class ExposureChecks {

  private static final String MESSAGE = "message";

  private ExposureChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("exposed-state", ExposureChecks::exposedState),
        entry("static-field-writes", ExposureChecks::staticFieldWrites),
        entry(
            "inner-class-use",
            toml ->
                new InnerClassUseCheck(
                    toml.getBoolean("anonymous", () -> false),
                    toml.getBoolean("constructor-only", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "inner-serializable",
            toml ->
                new InnerSerializableCheck(
                    toml.getBoolean("outer-serializable", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "non-serializable-value",
            toml ->
                new NonSerializableValueCheck(
                    toml.getBoolean("session", () -> false), TomlValues.required(toml, MESSAGE))),
        entry(
            "transient-field-not-restored",
            toml -> new TransientFieldCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "bad-serializable-field",
            toml -> new BadSerializableFieldCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "servlet-field",
            toml ->
                new ServletFieldCheck(
                    toml.getBoolean("struts", () -> false),
                    toml.getBoolean("mutable", () -> false),
                    TomlValues.required(toml, MESSAGE))));
  }

  private static ExposedStateCheck exposedState(final TomlParseResult toml) {
    return new ExposedStateCheck(
        new ExposedStateCheck.Criteria(
            toml.getBoolean("store", () -> false),
            toml.getBoolean("static", () -> false),
            toml.getBoolean("buffers", () -> false),
            Pattern.compile(TomlValues.required(toml, "types")),
            toml.getBoolean("public-static-method", () -> false)),
        TomlValues.required(toml, MESSAGE));
  }

  private static StaticFieldWritesCheck staticFieldWrites(final TomlParseResult toml) {
    return new StaticFieldWritesCheck(
        TomlValues.required(toml, "mode"),
        Pattern.compile(toml.getString("types", () -> "")),
        TomlValues.required(toml, MESSAGE));
  }
}
