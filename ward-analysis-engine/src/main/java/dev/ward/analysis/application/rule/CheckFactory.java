package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.rule.check.RuleCheck;
import org.tomlj.TomlParseResult;

/** Builds one named check from the parameters of a rule body. */
@FunctionalInterface
public interface CheckFactory {

  /**
   * Builds the check described by {@code toml}.
   *
   * @throws dev.ward.analysis.domain.rule.RuleConfigurationException if a parameter is missing or
   *     unusable
   */
  RuleCheck create(TomlParseResult toml);
}
