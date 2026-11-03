package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports functions that take more formal parameters than {@code limit}, counting each destructured
 * parameter once as ESLint {@code max-params} does.
 *
 * @param definition the rule this check was built from
 * @param limit the largest allowed number of parameters
 */
public record ParameterCountRule(RuleDefinition definition, int limit) implements CompiledRule {

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode function : SyntaxTrees.functions(root)) {
      final int count = SyntaxTrees.parameterCount(function);
      if (count > limit) {
        matches.add(
            new RuleMatch(
                function.span(), "Function has " + count + " parameters (limit " + limit + ")"));
      }
    }
    return matches;
  }
}
