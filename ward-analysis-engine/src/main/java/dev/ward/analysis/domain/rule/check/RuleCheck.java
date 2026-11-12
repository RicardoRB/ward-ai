package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.Measured;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.UnitTypeFacts;
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
   * Returns the value this check measures for every declaration in {@code root}, including those
   * within its limit. A check that measures nothing returns none, as this default method does.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the check cannot run on the
   *     tree
   */
  default List<Measured> measures(final SyntaxNode root) {
    return List.of();
  }

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

  /**
   * Returns the places in {@code root} where the check fails, given the control-flow graphs, the
   * scopes and the file. A check that reads no scopes needs only {@link #matches(SyntaxNode,
   * FlowView, SourceUnit)}.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the check cannot run on the
   *     tree
   */
  default List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView scopes, final SourceUnit unit) {
    return matches(root, flow, unit);
  }

  /**
   * What this check reads from the type facts. A check with capability {@code TYPES} declares the
   * node kinds and fact kinds it asks about, so one batched request per unit serves every rule.
   */
  default FactNeed needs() {
    return FactNeed.NONE;
  }

  /**
   * Returns the places in {@code root} where the check fails, given the control-flow graphs, the
   * scopes, the type facts and the file. Facts the resolver could not answer are unknown, and a
   * check must report nothing that depends on them. A check that reads no type facts needs only
   * {@link #matches(SyntaxNode, FlowView, ScopeView, SourceUnit)}.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the check cannot run on the
   *     tree
   */
  default List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return matches(root, flow, scopes, unit);
  }
}
