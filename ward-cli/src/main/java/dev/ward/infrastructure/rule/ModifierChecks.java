package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.AnnotationLocationCheck;
import dev.ward.analysis.domain.rule.check.ModifierOrderCheck;
import dev.ward.analysis.domain.rule.check.RecordAccessorOverrideCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java modifier and annotation checks that a rule body names. Each parameter is a rule
 * option, named after the Checkstyle property in kebab-case.
 */
final class ModifierChecks {

  private static final String MESSAGE = "message";
  private static final String ALLOW_SINGLE_PARAMETERLESS =
      "allow-sameline-single-parameterless-annotation";
  private static final String ALLOW_PARAMETERIZED = "allow-sameline-parameterized-annotation";

  private static final Set<String> CHECKS =
      Set.of("modifier-order", "annotation-location", "missing-override-on-record-accessor");

  private ModifierChecks() {}

  /** Returns whether {@code name} is one of the modifier and annotation checks. */
  static boolean isModifierCheck(final String name) {
    return CHECKS.contains(name);
  }

  /**
   * Builds the check named {@code name}.
   *
   * @throws dev.ward.analysis.domain.rule.RuleConfigurationException if a parameter is invalid
   */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "modifier-order" -> new ModifierOrderCheck(message(toml));
      case "annotation-location" ->
          new AnnotationLocationCheck(
              flag(toml, ALLOW_SINGLE_PARAMETERLESS, true),
              flag(toml, ALLOW_PARAMETERIZED, false),
              message(toml));
      default -> new RecordAccessorOverrideCheck(message(toml));
    };
  }

  private static boolean flag(
      final TomlParseResult toml, final String key, final boolean fallback) {
    return Optional.ofNullable(toml.getBoolean(key)).orElse(fallback);
  }

  private static String message(final TomlParseResult toml) {
    return TomlValues.required(toml, MESSAGE);
  }
}
