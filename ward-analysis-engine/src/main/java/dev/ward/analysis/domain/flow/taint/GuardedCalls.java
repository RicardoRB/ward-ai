package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * Lets a method that checks its parameter against an allow-list vouch for the argument of its
 * callers. The frontend marks a check that leaves its variable clean with a site named {@value
 * #GUARD}, whose qualifier is the variable. A method <em>checks</em> a parameter when every path
 * that returns from it, rather than throws, passes that mark and nothing else assigns the
 * parameter. After a call to such a method, the caller's argument, when it is a bare local
 * variable, is written clean, as the frontend does for a check in the caller itself.
 */
final class GuardedCalls {
  /** The name of the site that a frontend gives an allow-list check that leaves a value clean. */
  static final String GUARD = "<allow-listed>";

  /** The write sits past every node, so no statement contains it and it takes no taint. */
  private static final int PAST_END = Integer.MAX_VALUE;

  private GuardedCalls() {}

  /** A method with the argument positions it checks. */
  private record Checking(LocalMethod method, Set<Integer> parameters) {}

  /**
   * Returns what puts a clean write after each call to a method of {@code methods} that checks an
   * argument; the identity when no method checks one.
   */
  static UnaryOperator<ControlFlowGraph> over(final Map<String, LocalMethod> methods) {
    final List<Checking> checking = new ArrayList<>();
    for (final LocalMethod method : methods.values()) {
      final Set<Integer> parameters = checkedParameters(method);
      if (!parameters.isEmpty()) {
        checking.add(new Checking(method, parameters));
      }
    }
    return checking.isEmpty() ? graph -> graph : graph -> apply(graph, checking);
  }

  private static ControlFlowGraph apply(
      final ControlFlowGraph graph,
      final List<Checking> checking
  ) {
    final List<BasicBlock> blocks = new ArrayList<>(graph.blocks().size());
    boolean changed = false;
    for (final BasicBlock block : graph.blocks()) {
      final BasicBlock applied = apply(block, checking);
      changed |= applied != block;
      blocks.add(applied);
    }
    return changed
        ? new ControlFlowGraph(graph.body(), graph.span(), blocks, graph.entry(), graph.exit())
        : graph;
  }

  private static BasicBlock apply(final BasicBlock block, final List<Checking> checking) {
    final List<VariableAccess> accesses = new ArrayList<>(block.accesses());
    boolean changed = false;
    for (final FlowSite site : block.sites()) {
      if (site.kind() == FlowSiteKind.CALL) {
        changed |= cleanArguments(site, checking, accesses);
      }
    }
    return changed
        ? new BasicBlock(block.id(), block.nodes(), block.successors(), accesses, block.sites())
        : block;
  }

  private static boolean cleanArguments(
      final FlowSite site,
      final List<Checking> checking,
      final List<VariableAccess> accesses
  ) {
    boolean changed = false;
    for (final Checking candidate : checking) {
      if (calls(candidate.method(), site)) {
        for (final int index : candidate.parameters()) {
          changed |= cleanArgument(site, site.arguments().get(index), accesses);
        }
      }
    }
    return changed;
  }

  static boolean calls(final LocalMethod method, final FlowSite site) {
    return method.arity() == site.arguments().size()
        && method.qualifiers().stream()
            .anyMatch(
                qualifier ->
                    new TaintEntry(
                        FlowSiteKind.CALL,
                        method.name(),
                        Optional.empty(),
                        Optional.of(qualifier),
                        List.of()
                    ).matches(site)
            );
  }

  /** Writes the variable that the argument names clean, right after the last access of the call. */
  private static boolean cleanArgument(
      final FlowSite site,
      final Span argument,
      final List<VariableAccess> accesses
  ) {
    VariableAccess read = null;
    int last = -1;
    for (int index = 0; index < accesses.size(); index++) {
      final VariableAccess access = accesses.get(index);
      if (TaintSpans.contains(site.span(), access.span())) {
        last = index;
      }
      if (access.kind() == AccessKind.READ && access.span().equals(argument)) {
        read = access;
      }
    }
    if (read == null) {
      return false;
    }
    final Span call = site.span();
    final Span past =
        new Span(
            call.endLine(),
            call.endColumn(),
            call.endLine(),
            call.endColumn(),
            PAST_END,
            PAST_END
        );
    accesses.add(
        last + 1,
        new VariableAccess(read.name(), AccessKind.WRITE, past, read.declaration())
    );
    return true;
  }

  /** The positions of the parameters that the method checks on every path that returns. */
  static Set<Integer> checkedParameters(final LocalMethod method) {
    final Set<Integer> checked = new TreeSet<>();
    final List<String> parameters = method.parameters();
    final ControlFlowGraph graph = method.graph();
    if (parameters.size() != method.arity()) {
      return checked;
    }
    for (int index = 0; index < parameters.size(); index++) {
      final int guard = guardBlock(graph, parameters.get(index));
      if (guard >= 0 && !returnsAround(graph, guard)) {
        checked.add(index);
      }
    }
    return checked;
  }

  /** The block of the single check of a parameter that nothing else assigns, or -1. */
  private static int guardBlock(final ControlFlowGraph graph, final String parameter) {
    int found = -1;
    Span mark = null;
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowSite site : block.sites()) {
        if (GUARD.equals(site.name()) && parameter.equals(site.qualifier())) {
          if (found >= 0) {
            return -1;
          }
          found = block.id();
          mark = site.span();
        }
      }
    }
    return found >= 0 && assignedOnly(graph, parameter, mark) ? found : -1;
  }

  private static boolean assignedOnly(
      final ControlFlowGraph graph,
      final String parameter,
      final Span mark
  ) {
    return graph.blocks().stream()
        .flatMap(block -> block.accesses().stream())
        .noneMatch(
            access ->
                access.kind() == AccessKind.WRITE
                    && access.name().equals(parameter)
                    && !access.span().equals(mark)
        );
  }

  /**
   * Whether control can reach the exit without passing the block and without throwing. A throw
   * ends in the exit through an exception edge, so only the other edges to the exit count.
   */
  private static boolean returnsAround(final ControlFlowGraph graph, final int guard) {
    final Set<Integer> seen = new HashSet<>();
    final Deque<Integer> pending = new ArrayDeque<>(List.of(graph.entry()));
    while (!pending.isEmpty()) {
      final int block = pending.pop();
      if (block == guard || !seen.add(block)) {
        continue;
      }
      for (final FlowEdge edge : graph.blocks().get(block).successors()) {
        if (edge.target() == graph.exit() && edge.kind() != FlowEdgeKind.EXCEPTION) {
          return true;
        }
        pending.push(edge.target());
      }
    }
    return false;
  }
}
