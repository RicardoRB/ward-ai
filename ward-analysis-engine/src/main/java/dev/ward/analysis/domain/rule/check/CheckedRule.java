package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;

/** A rule whose matches come from one {@link RuleCheck}. */
public final class CheckedRule implements CompiledRule {

  private final RuleDefinition definition;
  private final RuleCheck check;

  /**
   * Binds a definition to its check.
   *
   * @throws NullPointerException if an argument is missing
   */
  public CheckedRule(final RuleDefinition definition, final RuleCheck check) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.check = Objects.requireNonNull(check, "check");
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return check.matches(root);
  }
}
