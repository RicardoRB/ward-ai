package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.DesignForExtensionCheck;
import dev.ward.analysis.domain.rule.check.FinalClassCheck;
import dev.ward.analysis.domain.rule.check.HideUtilityClassConstructorCheck;
import dev.ward.analysis.domain.rule.check.InnerTypeLastCheck;
import dev.ward.analysis.domain.rule.check.InterfaceIsTypeCheck;
import dev.ward.analysis.domain.rule.check.MutableExceptionCheck;
import dev.ward.analysis.domain.rule.check.OneTopLevelClassCheck;
import dev.ward.analysis.domain.rule.check.SealedShouldHavePermitsListCheck;
import dev.ward.analysis.domain.rule.check.ThrowsCountCheck;
import dev.ward.analysis.domain.rule.check.VisibilityModifierCheck;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java class design checks that a rule body names, with their parameters. Each parameter
 * is a rule option, named after the Checkstyle property in kebab-case.
 */
final class DesignChecks {

  private static final String MESSAGE = "message";
  private static final String FORMAT = "format";
  private static final String EXTENDED_FORMAT = "extended-class-name-format";
  private static final String IGNORE_PRIVATE = "ignore-private-methods";

  private DesignChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(types(), members()));
  }

  private static Map<String, CheckFactory> types() {
    return Map.ofEntries(
        entry("one-top-level-class", toml -> new OneTopLevelClassCheck(message(toml))),
        entry("final-class", toml -> new FinalClassCheck(message(toml))),
        entry("inner-type-last", toml -> new InnerTypeLastCheck(message(toml))),
        entry(
            "sealed-should-have-permits-list",
            toml -> new SealedShouldHavePermitsListCheck(message(toml))),
        entry(
            "hide-utility-class-constructor",
            toml ->
                new HideUtilityClassConstructorCheck(
                    TomlValues.optionalStrings(toml, "ignore-annotated-by"), message(toml))),
        entry(
            "interface-is-type",
            toml ->
                new InterfaceIsTypeCheck(
                    flag(toml, "allow-marker-interfaces", true), message(toml))),
        entry(
            "mutable-exception",
            toml ->
                new MutableExceptionCheck(
                    Pattern.compile(TomlValues.required(toml, FORMAT)),
                    Pattern.compile(TomlValues.required(toml, EXTENDED_FORMAT)),
                    message(toml))));
  }

  private static Map<String, CheckFactory> members() {
    return Map.ofEntries(
        entry(
            "throws-count",
            toml ->
                new ThrowsCountCheck(
                    TomlValues.integer(toml, "max"),
                    flag(toml, IGNORE_PRIVATE, true),
                    message(toml))),
        entry(
            "visibility-modifier",
            toml -> new VisibilityModifierCheck(visibility(toml), message(toml))),
        entry(
            "design-for-extension",
            toml ->
                new DesignForExtensionCheck(
                    TomlValues.optionalStrings(toml, "ignored-annotations"),
                    Pattern.compile(TomlValues.required(toml, "required-javadoc-phrase")),
                    message(toml))));
  }

  private static VisibilityModifierCheck.FieldVisibility visibility(final TomlParseResult toml) {
    return new VisibilityModifierCheck.FieldVisibility(
        flag(toml, "allow-public-final-fields", false),
        flag(toml, "allow-public-immutable-fields", false),
        flag(toml, "package-allowed", false),
        flag(toml, "protected-allowed", false),
        Pattern.compile(TomlValues.required(toml, "public-member-pattern")),
        TomlValues.optionalStrings(toml, "ignore-annotation-canonical-names"),
        TomlValues.optionalStrings(toml, "immutable-class-canonical-names"));
  }

  private static boolean flag(
      final TomlParseResult toml, final String key, final boolean fallback) {
    return Optional.ofNullable(toml.getBoolean(key)).orElse(fallback);
  }

  private static String message(final TomlParseResult toml) {
    return TomlValues.required(toml, MESSAGE);
  }
}
