package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.CallFactsCheck;
import dev.ward.analysis.domain.rule.check.MethodOverrideCheck;
import dev.ward.analysis.domain.rule.check.ProtoStringEqualityCheck;
import dev.ward.analysis.domain.rule.check.RequiredModifiersCheck;
import dev.ward.analysis.domain.rule.check.ThreadSafeFieldsCheck;
import java.util.Map;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * Builds the checks that read the annotations and supertypes the type facts give for calls and
 * method declarations, and the checks of the annotations of one file.
 */
final class FactCallChecks {

  private static final String MESSAGE = "message";

  private FactCallChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("call-facts", FactCallChecks::callFacts),
        entry("method-override", FactCallChecks::methodOverride),
        entry(
            "thread-safe-fields",
            toml -> new ThreadSafeFieldsCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "proto-string-equality",
            toml -> new ProtoStringEqualityCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "required-modifiers",
            toml -> new RequiredModifiersCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static CallFactsCheck callFacts(final TomlParseResult toml) {
    return new CallFactsCheck(
        new CallFactsCheck.Criteria(
            TomlValues.optionalStrings(toml, "declaring-types"),
            TomlValues.optionalStrings(toml, "names"),
            TomlValues.optionalStrings(toml, "method-annotations"),
            TomlValues.optionalStrings(toml, "declaring-annotations"),
            TomlValues.optionalStrings(toml, "receiver-ancestors"),
            Pattern.compile(toml.getString("text", () -> "")),
            toml.getBoolean("ignored", () -> false),
            toml.getBoolean("outside-resource", () -> false)),
        TomlValues.required(toml, MESSAGE));
  }

  private static MethodOverrideCheck methodOverride(final TomlParseResult toml) {
    return new MethodOverrideCheck(
        new MethodOverrideCheck.Criteria(
            TomlValues.strings(toml, "overridden-annotations"),
            TomlValues.optionalStrings(toml, "required-annotations"),
            toml.getBoolean("require-super-call", () -> false)),
        TomlValues.required(toml, MESSAGE));
  }
}
