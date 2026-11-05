package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;

/** Runs the unused-assignment check on one hand-built graph and reads its findings. */
final class CheckResults {

  private static final String MESSAGE = "the value assigned to '{value}' is never read";
  private static final SyntaxNode ROOT =
      new SyntaxNode("file", new Span(1, 1, 1, 2, 0, 1), Map.of(), List.of());

  private CheckResults() {}

  static List<String> messages(final ControlFlowGraph graph) {
    return check(graph).stream().map(RuleMatch::message).toList();
  }

  static List<Integer> lines(final ControlFlowGraph graph) {
    return check(graph).stream().map(match -> match.span().startLine()).toList();
  }

  private static List<RuleMatch> check(final ControlFlowGraph graph) {
    return new UnusedAssignmentCheck(MESSAGE).matches(ROOT, new FlowView(List.of(graph)));
  }
}
