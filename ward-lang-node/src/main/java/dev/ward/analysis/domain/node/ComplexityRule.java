package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports functions whose cyclomatic complexity is above {@code limit}. A function starts at one,
 * and each branch, loop, case, catch, logical operator and conditional adds one, as ESLint {@code
 * complexity} counts them.
 *
 * @param definition the rule this check was built from
 * @param limit the largest allowed complexity
 */
public record ComplexityRule(RuleDefinition definition, int limit) implements CompiledRule {

  private static final int BASE_COMPLEXITY = 1;

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode function : SyntaxTrees.functions(root)) {
      final int complexity = BASE_COMPLEXITY + SyntaxTrees.decisionPoints(function);
      if (complexity > limit) {
        matches.add(
            new RuleMatch(
                function.span(),
                "Function has complexity " + complexity + " (limit " + limit + ")"));
      }
    }
    return matches;
  }
}
