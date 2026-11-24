package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The parameters of a local method that reach a sink, for its summary in {@link TaintFile}. */
final class SinkParameters {
  private SinkParameters() {}

  /** The parameters that reach a sink, each with the assumptions of its best flow. */
  static Map<Integer, List<String>> of(final LocalMethod method, final TaintSpec seeded) {
    final Map<Integer, List<String>> sinks = new TreeMap<>();
    final List<String> parameters = method.parameters();
    if (parameters.size() != method.arity() || !TaintSpans.mayReachSink(method.graph(), seeded)) {
      return sinks;
    }
    for (int index = 0; index < parameters.size(); index++) {
      final ControlFlowGraph marked = method.withTaintedParameter(parameters.get(index));
      final int parameter = index;
      TaintAnalysis.run(marked, seeded).stream()
          .filter(finding -> LocalMethod.mark().equals(finding.source()))
          .map(TaintFinding::assumptions)
          .min(Comparator.comparingInt(List::size))
          .ifPresent(assumed -> sinks.put(parameter, assumed));
    }
    return sinks;
  }

  /**
   * The assumptions of a method's sink entry, which lists all its sink parameters. shortcut: the
   * entry is proven when one parameter's flow is, so another parameter's assumed flow then counts
   * as proven too; give each parameter its own entry if that shows up as a false positive.
   */
  static List<String> assumptions(final Map<Integer, List<String>> flows) {
    if (flows.values().stream().anyMatch(List::isEmpty)) {
      return List.of();
    }
    return flows.values().stream().flatMap(List::stream).distinct().sorted().toList();
  }
}
