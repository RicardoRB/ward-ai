package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeSafeValues;
import dev.ward.analysis.domain.node.NodeSinkValues;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports the sink expressions that the pattern finds, unless the value they hand over is safe: a
 * constant, a number, or a name that only such values reach (see {@link NodeSafeValues}). In host
 * mode the value is the URL of a request, and it is safe when its host is the address that a
 * server reports for itself.
 */
public final class NodeUnsafeValueCheck implements RuleCheck {
  private static final Set<String> KINDS = Set.of("singleExpression");

  private final Pattern text;
  private final boolean first;
  private final NodeSafeValues.Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param text the pattern the text of a sink expression must contain
   * @param first whether a call hands over its first argument, or its last
   * @param mode which values are safe besides constants
   * @param message the message of each finding
   */
  public NodeUnsafeValueCheck(
      final Pattern text,
      final boolean first,
      final NodeSafeValues.Mode mode,
      final String message
  ) {
    this.text = text;
    this.first = first;
    this.mode = mode;
    this.message = message;
  }

  /**
   * Always fails: the check reads the control-flow graphs of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unsafe-value check needs the data-flow graphs");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final NodeSafeValues safe = new NodeSafeValues(flow.graphs(), mode);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, KINDS)) {
      final SyntaxNode node = visit.node();
      if (text.matcher(SyntaxWalk.text(node)).find() && !holds(node, safe)) {
        found.add(
            CheckSupport.at(node.span(), CheckSupport.fill(message, SyntaxWalk.text(node), ""))
        );
      }
    }
    return found;
  }

  private boolean holds(final SyntaxNode node, final NodeSafeValues safe) {
    final Optional<SyntaxNode> value = NodeSinkValues.of(node, first);
    if (value.isEmpty()) {
      return false;
    }
    return mode == NodeSafeValues.Mode.LOCAL_HOST
        ? safe.isLocalUrl(value.get())
        : safe.isSafe(value.get());
  }
}
