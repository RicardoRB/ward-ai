package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.DeclarationOrderCheck;
import dev.ward.analysis.domain.rule.check.IllegalInstantiationCheck;
import dev.ward.analysis.domain.rule.check.IllegalTypeCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Set;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java declaration checks of the strict preset that a rule body names. Each parameter is
 * a rule option, named after the Checkstyle property in kebab-case.
 */
final class StrictDeclarationChecks {

  private static final String MESSAGE = "message";

  private static final Set<String> CHECKS =
      Set.of("declaration-order", "illegal-type", "illegal-instantiation");

  /** Checkstyle's default class names of IllegalType, simple and qualified. */
  private static final Set<String> ILLEGAL_TYPE_DEFAULTS =
      Set.of(
          "HashMap",
          "HashSet",
          "LinkedHashMap",
          "LinkedHashSet",
          "TreeMap",
          "TreeSet",
          "java.util.HashMap",
          "java.util.HashSet",
          "java.util.LinkedHashMap",
          "java.util.LinkedHashSet",
          "java.util.TreeMap",
          "java.util.TreeSet");

  private StrictDeclarationChecks() {}

  /** Returns whether {@code name} is one of the strict declaration checks. */
  static boolean isStrictDeclarationCheck(final String name) {
    return CHECKS.contains(name);
  }

  /**
   * Builds the check named {@code name}.
   *
   * @throws dev.ward.analysis.domain.rule.RuleConfigurationException if a parameter is invalid
   */
  static RuleCheck build(final String name, final TomlParseResult toml) {
    return switch (name) {
      case "illegal-type" -> illegalType(toml);
      case "illegal-instantiation" ->
          new IllegalInstantiationCheck(
              TomlValues.optionalStrings(toml, "classes"), TomlValues.required(toml, MESSAGE));
      default -> new DeclarationOrderCheck(TomlValues.required(toml, MESSAGE));
    };
  }

  private static RuleCheck illegalType(final TomlParseResult toml) {
    final Set<String> configured = TomlValues.optionalStrings(toml, "illegal-class-names");
    return new IllegalTypeCheck(
        configured.isEmpty() ? ILLEGAL_TYPE_DEFAULTS : configured,
        TomlValues.required(toml, MESSAGE));
  }
}
