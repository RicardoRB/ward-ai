package dev.ward.infrastructure.rule;

import java.util.Objects;

/**
 * One parameter of a generic check that a user may change: its name in the rule body, its type, and
 * its value in the body (empty when the body does not set it).
 *
 * @param name the parameter name in the body, for example {@code max}
 * @param type {@code string}, {@code integer} or {@code string-list}
 * @param defaultValue the value the body sets, as text; a list is comma-separated
 */
public record CheckOption(String name, String type, String defaultValue) {

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
