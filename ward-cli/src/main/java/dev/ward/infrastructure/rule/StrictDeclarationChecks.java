package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.check.DeclarationOrderCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Set;
import org.tomlj.TomlParseResult;

/**
 * Builds the Java declaration checks of the strict preset that a rule body names. Each parameter is
 * a rule option, named after the Checkstyle property in kebab-case.
 */
final class StrictDeclarationChecks {

  private static final String MESSAGE = "message";

  private static final Set<String> CHECKS = Set.of("declaration-order");

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
    return new DeclarationOrderCheck(TomlValues.required(toml, MESSAGE));
  }
}
