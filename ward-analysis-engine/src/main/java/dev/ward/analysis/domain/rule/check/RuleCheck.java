package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.rule.TypeNeed;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Set;

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

  /** The type facts this check reads; empty for a check that reads no types. */
  default Set<TypeNeed> typeNeeds() {
    return Set.of();
  }

  /**
   * Returns the places in {@code root} where the check fails, given the control-flow graphs, the
   * scopes, the file and the type facts of the file. A check that reads no types needs only {@link
   * #matches(SyntaxNode, FlowView, ScopeView, SourceUnit)}.
   */
  default List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final SourceUnit unit,
      final UnitTypeFacts types) {
    return matches(root, flow, scopes, unit);
  }
}
