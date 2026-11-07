package dev.ward.analysis.domain.rule;

/**
 * The {@code ward:<language>/duplicate-code} rules. They compare the tokens of every file of their
 * language, so they run once per run over the whole project, not per unit.
 */
public final class DuplicateCodeRule {

  /** The rule name shared by every language, after the slash of its id. */
  public static final String NAME = "duplicate-code";

  private static final String SUFFIX = "/" + NAME;

  private DuplicateCodeRule() {}

  /** Whether {@code definition} is a duplicate-code rule of one language. */
  public static boolean isDuplicateRule(final RuleDefinition definition) {
    return definition.id().endsWith(SUFFIX);
  }
}
