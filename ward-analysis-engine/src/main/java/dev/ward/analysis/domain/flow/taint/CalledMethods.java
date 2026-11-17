package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Picks the methods that the calls of a file can reach, so the analysis reads only those. */
final class CalledMethods {
  private CalledMethods() {}

  /** Keeps the methods of the file that some call of the file names with the right arity. */
  static Map<String, LocalMethod> local(
      final List<ControlFlowGraph> graphs,
      final Map<String,
      LocalMethod> methods
  ) {
    final Map<String, LocalMethod> kept = new HashMap<>(methods);
    kept.keySet().retainAll(callKeys(graphs));
    return kept;
  }

  /**
   * Keeps the methods of other files that a call in the file names, or that a kept method calls, by
   * name and arity.
   */
  static Map<String, LocalMethod> reachable(
      final List<ControlFlowGraph> graphs,
      final Map<String,
      LocalMethod> external
  ) {
    final Map<String, LocalMethod> kept = new HashMap<>();
    final Map<String, List<LocalMethod>> byCall =
        external.values().stream()
            .collect(
                Collectors.groupingBy(method -> LocalMethod.key(method.name(), method.arity()))
            );
    final Set<String> seen = new HashSet<>();
    final Deque<String> pending = new ArrayDeque<>(callKeys(graphs));
    while (!pending.isEmpty()) {
      final String call = pending.pop();
      if (!seen.add(call)) {
        continue;
      }
      for (final LocalMethod method : byCall.getOrDefault(call, List.of())) {
        if (kept.putIfAbsent(method.key(), method) == null) {
          pending.addAll(callKeys(List.of(method.graph())));
        }
      }
    }
    return kept;
  }

  private static Set<String> callKeys(final List<ControlFlowGraph> graphs) {
    final Set<String> calls = new HashSet<>();
    for (final ControlFlowGraph graph : graphs) {
      for (final BasicBlock block : graph.blocks()) {
        for (final FlowSite site : block.sites()) {
          if (site.kind() == FlowSiteKind.CALL) {
            calls.add(callKey(site));
          }
        }
      }
    }
    return calls;
  }

  private static String callKey(final FlowSite site) {
    return LocalMethod.key(site.name(), site.arguments().size());
  }
}
