package dev.ward.analysis.domain.rule;

import java.util.Objects;

/**
 * What must hold for the files a rule selects: one of the five constraints of the rule file.
 *
 * @param kind which constraint
 * @param target the selector of the constraint, for the dependency and location constraints; {@code
 *     null} for {@code file-name}
 * @param fileName the glob on the base name, for {@code file-name}; {@code null} otherwise
 */
public record ArchitectureConstraint(Kind kind, ArchitectureSelector target, String fileName) {

  /** The constraint keys of a rule, with the rule id of the family each one reports under. */
  public enum Kind {
    /** An edge from a selected file to a file of the target is a finding. */
    MUST_NOT_DEPEND_ON("must-not-depend-on", DependencyArchitectureRule.ID),
    /** An edge from a selected file to a file of neither the selection nor the target is. */
    MAY_ONLY_DEPEND_ON("may-only-depend-on", DependencyArchitectureRule.ID),
    /** An edge into a selected file from a file of neither the selection nor the target is. */
    ONLY_ACCESSED_BY("only-accessed-by", DependencyArchitectureRule.ID),
    /** A selected file whose base name does not match the glob is a finding. */
    FILE_NAME("file-name", NamingArchitectureRule.ID),
    /** A selected file that the target does not select is a finding. */
    MUST_BE_IN("must-be-in", LocationArchitectureRule.ID);

    private final String key;
    private final String ruleId;

    Kind(final String key, final String ruleId) {
      this.key = key;
      this.ruleId = ruleId;
    }

    /** The key in the rule file. */
    public String key() {
      return key;
    }

    /** The id of the rule family this constraint reports under. */
    public String ruleId() {
      return ruleId;
    }

    /** Whether the constraint reads dependency edges. */
    public boolean readsEdges() {
      return DependencyArchitectureRule.ID.equals(ruleId);
    }
  }

  /**
   * Validates the parts: {@code file-name} has a glob and no target, every other kind a target.
   *
   * @throws NullPointerException if the kind is missing
   * @throws IllegalArgumentException if the parts do not fit the kind
   */
  public ArchitectureConstraint {
    Objects.requireNonNull(kind, "kind");
    final boolean named = kind == Kind.FILE_NAME;
    if (named == (fileName == null) || named != (target == null)) {
      throw new IllegalArgumentException(kind.key() + " has the wrong value");
    }
  }
}
