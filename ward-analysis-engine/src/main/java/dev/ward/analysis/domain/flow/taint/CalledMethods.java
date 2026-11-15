package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Picks the methods that the calls of a file can reach, so the analysis reads only those. */
final class CalledMethods {

  private CalledMethods() {}

  /** Keeps the methods of the file that some call of the file names with the right arity. */
  static Map<String, LocalMethod> local(
      final List<ControlFlowGraph> graphs, final Map<String, LocalMethod> methods) {
    final Map<String, LocalMethod> kept = new HashMap<>(methods);
    kept.keySet().retainAll(callKeys(graphs));
    return kept;
  }

  /**
   * Keeps the methods of other files that a call in the file names, or that a kept method calls, by
   * name and arity.
   */
  static Map<String, LocalMethod> reachable(
      final List<ControlFlowGraph> graphs, final Map<String, LocalMethod> external) {
    final Map<String, LocalMethod> kept = new HashMap<>();
    final Set<String> calls = callKeys(graphs);
    boolean grown = !external.isEmpty();
    while (grown) {
      grown = false;
      for (final LocalMethod method : external.values()) {
        if (calls.contains(LocalMethod.key(method.name(), method.arity()))
            && kept.putIfAbsent(method.key(), method) == null) {
          calls.addAll(callKeys(List.of(method.graph())));
          grown = true;
        }
      }
    }
    return kept;
  }

  private static Set<String> callKeys(final List<ControlFlowGraph> graphs) {
    final Set<String> calls = new HashSet<>();
    for (final ControlFlowGraph graph : graphs) {
      for (final BasicBlock block : graph.blocks()) {
        block.sites().stream()
            .filter(site -> site.kind() == FlowSiteKind.CALL)
            .forEach(site -> calls.add(callKey(site)));
      }
    }
    return calls;
  }

  private static String callKey(final FlowSite site) {
    return LocalMethod.key(site.name(), site.arguments().size());
  }
}
