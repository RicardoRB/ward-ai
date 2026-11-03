package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports functions that span more lines than {@code limit}, counting blank and comment lines as
 * ESLint {@code max-lines-per-function} does by default.
 *
 * @param definition the rule this check was built from
 * @param limit the largest allowed number of lines
 */
public record FunctionLengthRule(RuleDefinition definition, int limit) implements CompiledRule {

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode function : SyntaxTrees.functions(SyntaxTrees.withoutErrors(root))) {
      final int lines = SyntaxTrees.lines(function.span());
      if (lines > limit) {
        matches.add(
            new RuleMatch(
                function.span(), "Function has " + lines + " lines (limit " + limit + ")"));
      }
    }
    return matches;
  }
}
