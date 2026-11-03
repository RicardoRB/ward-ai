package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;

/**
 * Reports a unit longer than {@code limit} lines. The count runs to the last token, so trailing
 * blank lines and comments are not counted; the parity report records that difference from ESLint.
 *
 * @param definition the rule this check was built from
 * @param limit the largest allowed number of lines
 */
public record FileLengthRule(RuleDefinition definition, int limit) implements CompiledRule {

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final int lines = SyntaxTrees.lastLine(SyntaxTrees.withoutErrors(root));
    if (lines <= limit) {
      return List.of();
    }
    return List.of(
        new RuleMatch(root.span(), "File has " + lines + " lines (limit " + limit + ")"));
  }
}
