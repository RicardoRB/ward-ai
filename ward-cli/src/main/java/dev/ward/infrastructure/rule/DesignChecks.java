package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.DesignForExtensionCheck;
import dev.ward.analysis.domain.rule.check.FinalClassCheck;
import dev.ward.analysis.domain.rule.check.HideUtilityClassConstructorCheck;
import dev.ward.analysis.domain.rule.check.InnerTypeLastCheck;
import dev.ward.analysis.domain.rule.check.InterfaceIsTypeCheck;
import dev.ward.analysis.domain.rule.check.MutableExceptionCheck;
import dev.ward.analysis.domain.rule.check.OneTopLevelClassCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.SealedShouldHavePermitsListCheck;
import dev.ward.analysis.domain.rule.check.ThrowsCountCheck;
import dev.ward.analysis.domain.rule.check.VisibilityModifierCheck;
import java.util.Optional;
import java.util.Set;
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

  private static final Set<String> CHECKS =
      Set.of(
          "one-top-level-class",
          "final-class",
          "hide-utility-class-constructor",
          "inner-type-last",
          "interface-is-type",
          "mutable-exception",
          "sealed-should-have-permits-list",
          "throws-count",
          "visibility-modifier",
          "design-for-extension");

  private DesignChecks() {}

  /** Returns whether {@code name} is one of the class design checks. */
  static boolean isDesignCheck(final String name) {
    return CHECKS.contains(name);
  }

  /**
   * Builds the check named {@code name}.
   *
   * @throws RuleConfigurationException if the name is unknown or a parameter is invalid
   */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "one-top-level-class" -> new OneTopLevelClassCheck(message(toml));
      case "final-class" -> new FinalClassCheck(message(toml));
      case "inner-type-last" -> new InnerTypeLastCheck(message(toml));
      case "sealed-should-have-permits-list" -> new SealedShouldHavePermitsListCheck(message(toml));
      default -> buildWithOptions(name, toml);
    };
  }

  /** Builds the checks whose parameters are options, and the checks that have none of their own. */
  private static RuleCheck buildWithOptions(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "hide-utility-class-constructor" ->
          new HideUtilityClassConstructorCheck(
              TomlValues.optionalStrings(toml, "ignore-annotated-by"), message(toml));
      case "interface-is-type" ->
          new InterfaceIsTypeCheck(flag(toml, "allow-marker-interfaces", true), message(toml));
      case "mutable-exception" ->
          new MutableExceptionCheck(
              Pattern.compile(TomlValues.required(toml, FORMAT)),
              Pattern.compile(TomlValues.required(toml, EXTENDED_FORMAT)),
              message(toml));
      case "throws-count" ->
          new ThrowsCountCheck(
              TomlValues.integer(toml, "max"), flag(toml, IGNORE_PRIVATE, true), message(toml));
      case "visibility-modifier" -> new VisibilityModifierCheck(visibility(toml), message(toml));
      case "design-for-extension" ->
          new DesignForExtensionCheck(
              TomlValues.optionalStrings(toml, "ignored-annotations"),
              Pattern.compile(TomlValues.required(toml, "required-javadoc-phrase")),
              message(toml));
      default -> throw new RuleConfigurationException("unknown check '" + name + "'");
    };
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
