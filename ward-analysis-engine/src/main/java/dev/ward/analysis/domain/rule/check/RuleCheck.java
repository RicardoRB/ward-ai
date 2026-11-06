package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;

/** One check of a declarative rule, run over the syntax tree of a source unit. */
@FunctionalInterface
public interface RuleCheck {

  /**
   * Returns the places in {@code root} where the check fails, in source order.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the check cannot run on the
   *     tree
   */
  List<RuleMatch> matches(SyntaxNode root);

  /**
   * Returns the places in {@code root} where the check fails, given the control-flow graphs of the
   * unit. A check that reads no graphs needs only {@link #matches(SyntaxNode)}.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the check cannot run on the
   *     tree
   */
  default List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    return matches(root);
  }

  /**
   * Returns the places in {@code root} where the check fails, given the control-flow graphs and the
   * file. A check that reads neither the path nor the text needs only {@link #matches(SyntaxNode,
   * FlowView)}.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the check cannot run on the
   *     tree
   */
  default List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return matches(root, flow);
  }
}
