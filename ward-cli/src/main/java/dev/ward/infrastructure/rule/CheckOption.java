package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleOptionType;
import java.util.Objects;

/**
 * One parameter of a generic check that a user may change: its name in the rule body, its type, and
 * its value in the body (empty when the body does not set it).
 *
 * @param name the parameter name in the body, for example {@code max}
 * @param type as {@code ward-rules.toml} declares it
 * @param defaultValue the value the body sets, as text; a list is comma-separated
 */
public record CheckOption(String name, RuleOptionType type, String defaultValue) {

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public CheckOption {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(defaultValue, "defaultValue");
  }
}
