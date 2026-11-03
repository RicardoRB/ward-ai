package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports a block nested deeper than {@code limit} statements of {@code if}, loop, {@code switch},
 * {@code try} or {@code with}, counted per function as ESLint {@code max-depth} counts them. An
 * {@code else if} does not add a level.
 *
 * @param definition the rule this check was built from
 * @param limit the deepest allowed nesting
 */
public record NestingDepthRule(RuleDefinition definition, int limit) implements CompiledRule {

  private static final String BLOCK = "block";
  private static final String STATEMENT = "statement";
  private static final String IF = "ifStatement";
  private static final String ELSE_TOKEN = "'else'";

  /** Statements that nest their body one level deeper. */
  private static final Set<String> NESTING =
      Set.of(IF, "iterationStatement", "switchStatement", "tryStatement", "withStatement");

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode function : SyntaxTrees.functions(SyntaxTrees.withoutErrors(root))) {
      for (final SyntaxNode child : function.children()) {
        scan(child, 0, false, matches);
      }
    }
    return matches;
  }

  /**
   * Walks {@code node} with {@code depth} enclosing nesting statements. {@code elseBranch} is true
   * when {@code node} is the statement after an {@code else} or an {@code if} inside it.
   */
  private void scan(
      final SyntaxNode node,
      final int depth,
      final boolean elseBranch,
      final List<RuleMatch> matches) {
    if (BLOCK.equals(node.kind()) && depth > limit) {
      matches.add(
          new RuleMatch(node.span(), "Block is nested " + depth + " deep (limit " + limit + ")"));
    }
    final boolean elseIf = elseBranch && IF.equals(node.kind());
    final int inner = NESTING.contains(node.kind()) && !elseIf ? depth + 1 : depth;
    final List<SyntaxNode> children = node.children();
    for (int index = 0; index < children.size(); index++) {
      final SyntaxNode child = children.get(index);
      if (!SyntaxTrees.isFunction(child)) {
        scan(child, inner, isElseChild(node, index, elseBranch), matches);
      }
    }
  }

  private static boolean isElseChild(
      final SyntaxNode parent, final int index, final boolean parentIsElseBranch) {
    final boolean afterElse =
        IF.equals(parent.kind())
            && index > 0
            && ELSE_TOKEN.equals(parent.children().get(index - 1).kind());
    final boolean wrapsElseIf = STATEMENT.equals(parent.kind()) && parentIsElseBranch;
    return afterElse || wrapsElseIf;
  }
}
