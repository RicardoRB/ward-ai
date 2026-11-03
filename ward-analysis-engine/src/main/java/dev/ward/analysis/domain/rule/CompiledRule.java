package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** A rule ready to run over syntax trees. A {@link RuleMatcher} builds it from a definition. */
public interface CompiledRule {

  /** Returns the definition this rule was built from. */
  RuleDefinition definition();

  /**
   * Returns the matches of this rule in the tree rooted at {@code root}.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     tree
   */
  List<RuleMatch> matches(SyntaxNode root);

  /**
   * Returns the matches of this rule in the tree rooted at {@code root}, given the files around it.
   * A rule that reads no dependencies needs only {@link #matches(SyntaxNode)}, which this default
   * method delegates to.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     tree
   */
  default List<RuleMatch> matches(final SyntaxNode root, final DependencyView dependencies) {
    return matches(root);
  }
}
