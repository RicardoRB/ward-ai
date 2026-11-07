package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Reports a loop that control can enter but never leave: blocks reachable from the entry of a body
 * from which the exit of the body is not reachable. A loop whose blocks call a method or create an
 * object is left out, since the call may block, throw or end the program.
 */
public final class NonTerminatingLoopCheck implements RuleCheck {
  private static final Set<String> CALLS = Set.of("methodCall", "creator");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the first node of the loop
   */
  public NonTerminatingLoopCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final ControlFlowGraph graph : flow.graphs()) {
      for (final Set<Integer> loop : trappedLoops(graph)) {
        if (!callsIn(graph, loop)) {
          position(graph, loop)
              .ifPresent(node -> matches.add(CheckSupport.at(node.span(), message)));
        }
      }
    }
    return List.copyOf(matches);
  }

  /** Returns the loops, as groups of connected blocks, that can be entered but not left. */
  private static List<Set<Integer>> trappedLoops(final ControlFlowGraph graph) {
    final Set<Integer> stuck = new TreeSet<>(reach(graph, graph.entry(), false));
    stuck.removeAll(reach(graph, graph.exit(), true));
    final Set<Integer> cyclic = new TreeSet<>();
    for (final int block : stuck) {
      if (onCycle(graph, block, stuck)) {
        cyclic.add(block);
      }
    }
    final List<Set<Integer>> groups = new ArrayList<>();
    final Set<Integer> assigned = new HashSet<>();
    for (final int start : cyclic) {
      if (assigned.add(start)) {
        final Set<Integer> group = connected(graph, start, cyclic);
        assigned.addAll(group);
        groups.add(group);
      }
    }
    return groups;
  }

  private static boolean onCycle(
      final ControlFlowGraph graph, final int block, final Set<Integer> within) {
    final Set<Integer> seen = new HashSet<>();
    final Deque<Integer> pending = new ArrayDeque<>();
    neighbours(graph, block, false).stream().filter(within::contains).forEach(pending::push);
    while (!pending.isEmpty()) {
      final int next = pending.pop();
      if (next == block) {
        return true;
      }
      if (seen.add(next)) {
        neighbours(graph, next, false).stream().filter(within::contains).forEach(pending::push);
      }
    }
    return false;
  }

  private static Set<Integer> reach(
      final ControlFlowGraph graph, final int start, final boolean backwards) {
    final Set<Integer> seen = new HashSet<>(Set.of(start));
    final Deque<Integer> pending = new ArrayDeque<>(List.of(start));
    while (!pending.isEmpty()) {
      final int block = pending.pop();
      for (final int next : neighbours(graph, block, backwards)) {
        if (seen.add(next)) {
          pending.push(next);
        }
      }
    }
    return seen;
  }

  private static Set<Integer> connected(
      final ControlFlowGraph graph, final int start, final Set<Integer> allowed) {
    final Set<Integer> seen = new TreeSet<>(Set.of(start));
    final Deque<Integer> pending = new ArrayDeque<>(List.of(start));
    while (!pending.isEmpty()) {
      final int block = pending.pop();
      Stream.concat(
              neighbours(graph, block, false).stream(), neighbours(graph, block, true).stream())
          .filter(allowed::contains)
          .filter(seen::add)
          .forEach(pending::push);
    }
    return seen;
  }

  private static List<Integer> neighbours(
      final ControlFlowGraph graph, final int block, final boolean backwards) {
    if (!backwards) {
      return targets(graph.blocks().get(block));
    }
    return graph.blocks().stream()
        .filter(other -> targets(other).contains(block))
        .map(BasicBlock::id)
        .toList();
  }

  /** Returns the blocks control can move to, without the false branch of a literal true. */
  private static List<Integer> targets(final BasicBlock block) {
    final boolean alwaysTrue =
        !block.nodes().isEmpty()
            && "true".equals(SyntaxWalk.text(block.nodes().get(block.nodes().size() - 1)));
    return block.successors().stream()
        .filter(edge -> !(alwaysTrue && edge.kind() == FlowEdgeKind.FALSE))
        .map(FlowEdge::target)
        .toList();
  }

  /** Returns the first node of the loop head, the block a back edge returns to. */
  private static Optional<SyntaxNode> position(
      final ControlFlowGraph graph, final Set<Integer> loop) {
    final Optional<Integer> head =
        loop.stream()
            .filter(
                block ->
                    neighbours(graph, block, true).stream()
                        .anyMatch(from -> loop.contains(from) && from >= block))
            .findFirst();
    return Stream.concat(head.stream(), loop.stream())
        .flatMap(block -> graph.blocks().get(block).nodes().stream())
        .findFirst();
  }

  private static boolean callsIn(final ControlFlowGraph graph, final Set<Integer> loop) {
    return loop.stream()
        .flatMap(block -> graph.blocks().get(block).nodes().stream())
        .flatMap(node -> SyntaxWalk.descendants(node).stream())
        .anyMatch(node -> CALLS.contains(node.kind()));
  }
}
