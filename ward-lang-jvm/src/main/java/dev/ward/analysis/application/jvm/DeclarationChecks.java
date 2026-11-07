package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.AnnotatedMemberCheck;
import dev.ward.analysis.domain.rule.check.HiddenFieldCheck;
import dev.ward.analysis.domain.rule.check.ParameterAssignmentCheck;
import dev.ward.analysis.domain.rule.check.VariableDeclarationUsageDistanceCheck;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java declaration checks: hidden fields, parameter assignments and the distance between
 * a local's declaration and its first use. Each parameter is a rule option, named after the
 * Checkstyle property in kebab-case.
 */
final class DeclarationChecks {

  private static final String MESSAGE = "message";
  private static final int DEFAULT_DISTANCE = 3;

  private DeclarationChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry("annotated-member", DeclarationChecks::annotatedMember),
        entry(
            "hidden-field",
            toml ->
                new HiddenFieldCheck(
                    flag(toml, "ignore-constructor-parameter", false),
                    flag(toml, "ignore-setter", false),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "parameter-assignment",
            toml -> new ParameterAssignmentCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "variable-declaration-usage-distance",
            toml ->
                new VariableDeclarationUsageDistanceCheck(
                    TomlValues.optionalInteger(toml, "allowed-distance", DEFAULT_DISTANCE),
                    flag(toml, "ignore-finals", true),
                    TomlValues.required(toml, MESSAGE))));
  }

  private static boolean flag(
      final TomlParseResult toml, final String key, final boolean fallback) {
    return toml.getBoolean(key, () -> fallback);
  }

  private static AnnotatedMemberCheck annotatedMember(final TomlParseResult toml) {
    return new AnnotatedMemberCheck(
        new AnnotatedMemberCheck.Criteria(
            TomlValues.required(toml, "declaration"),
            TomlValues.strings(toml, "annotations"),
            TomlValues.optionalStrings(toml, "modifiers"),
            TomlValues.optionalStrings(toml, "absent-modifiers")),
        TomlValues.required(toml, MESSAGE));
  }
}
