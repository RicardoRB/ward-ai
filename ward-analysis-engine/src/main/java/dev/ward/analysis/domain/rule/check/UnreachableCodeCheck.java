package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports the first statement of each run of code that no path from the entry of its body reaches,
 * such as the statements after a {@code return}, like ESLint {@code no-unreachable}. A run starts
 * at a block that no unreachable block leads to.
 */
public final class UnreachableCodeCheck implements RuleCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public UnreachableCodeCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: the check reads the control-flow graphs, which only {@link #matches(SyntaxNode,
   * FlowView)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unreachable-code check needs the control-flow graphs");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final ControlFlowGraph graph : flow.graphs()) {
      found.addAll(unreachable(graph));
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private RuleMatch match(final BasicBlock block) {
    return new RuleMatch(block.nodes().get(0).span(), message);
  }

  private List<RuleMatch> unreachable(final ControlFlowGraph graph) {
    final Set<Integer> live = reachable(graph);
    final List<BasicBlock> dead = new ArrayList<>();
    for (final BasicBlock block : graph.blocks()) {
      if (!live.contains(block.id())) {
        dead.add(block);
      }
    }
    final Set<Integer> deadIds = new HashSet<>();
    dead.forEach(block -> deadIds.add(block.id()));
    final Set<Integer> led = new HashSet<>();
    for (final BasicBlock block : dead) {
      block.successors().forEach(edge -> led.add(edge.target()));
    }
    final List<RuleMatch> found = new ArrayList<>();
    for (final BasicBlock block : dead) {
      if (!block.nodes().isEmpty() && !(led.contains(block.id()) && deadIds.contains(block.id()))) {
        found.add(match(block));
      }
    }
    return found;
  }

  private static Set<Integer> reachable(final ControlFlowGraph graph) {
    final Set<Integer> seen = new HashSet<>();
    final Deque<Integer> work = new ArrayDeque<>();
    work.push(graph.entry());
    while (!work.isEmpty()) {
      final int id = work.pop();
      if (seen.add(id)) {
        graph.blocks().get(id).successors().forEach(edge -> work.push(edge.target()));
      }
    }
    return seen;
  }
}
