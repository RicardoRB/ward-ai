package dev.ward.analysis.domain.rule;

import java.util.Objects;

/**
 * One rule of {@code ward-architecture.toml}: the files it is about, what must hold for them, and
 * why.
 *
 * @param name the name that is unique in the file and starts every finding
 * @param select the files the rule is about
 * @param constraint what must hold for them
 * @param because the reason that ends every finding
 * @param allowEmpty whether a rule that selects no file passes instead of failing the gate
 */
public record ArchitectureRule(
    String name,
    ArchitectureSelector select,
    ArchitectureConstraint constraint,
    String because,
    boolean allowEmpty) {

  /**
   * Validates the parts.
   *
   * @throws NullPointerException if a part is missing
   * @throws IllegalArgumentException if the name or reason is blank
   */
  public ArchitectureRule {
    Objects.requireNonNull(select, "select");
    Objects.requireNonNull(constraint, "constraint");
    if (Objects.requireNonNull(name, "name").isBlank()
        || Objects.requireNonNull(because, "because").isBlank()) {
      throw new IllegalArgumentException("a rule needs a name and a reason");
    }
  }

  /** The finding text: the name, what is wrong, and the reason. */
  String message(final String problem) {
    return name + ": " + problem + ", because " + because;
  }
}
