package dev.ward.analysis.domain.flow.taint;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the callers of one method need to know about it, for {@link TaintFile}. The assumptions are
 * those of the flow that makes the method return taint, and of the flows that take its parameters
 * to a sink.
 */
record MethodSummary(
    boolean returnsTaint,
    Set<Integer> sinkParameters,
    boolean cleanResult,
    Map<String,
    Set<Integer>> validated,
    List<String> returnAssumptions,
    List<String> sinkAssumptions
) {
  /** Copies the collections, keeping the parameters in order. */
  MethodSummary {
    sinkParameters = Collections.unmodifiableSortedSet(new TreeSet<>(sinkParameters));
    validated = Map.copyOf(validated);
    returnAssumptions = List.copyOf(returnAssumptions);
    sinkAssumptions = List.copyOf(sinkAssumptions);
  }

  /** What the analysis of a method decides: what it returns, passes to a sink and keeps clean. */
  static SummaryMemo.Core core(
      final LocalMethod method,
      final TaintSpec spec,
      final TaintSpec seeded
  ) {
    final Optional<Taint> returned = TaintAnalysis.returned(method.graph(), spec);
    final Map<Integer, List<String>> sinkFlows = SinkParameters.of(method, seeded);
    final boolean clean =
        returned.isEmpty() && sinkFlows.isEmpty() && CleanResults.of(method, seeded);
    return new SummaryMemo.Core(returned, sinkFlows, clean);
  }

  /** The summary of a method, or empty when its callers need to know nothing about it. */
  static Optional<MethodSummary> of(
      final LocalMethod method,
      final SummaryMemo.Core core,
      final Set<String> kinds,
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      Map<String,
      Set<Integer>>> validating
  ) {
    final Optional<Taint> returned = core.returned();
    final boolean returns = returned.isPresent();
    final Map<Integer, List<String>> sinkFlows = core.sinkFlows();
    final Set<Integer> sinks = new TreeSet<>(sinkFlows.keySet());
    final boolean clean = core.clean();
    final Map<String, Set<Integer>> validated =
        ValidatingMethods.of(method, kinds, methods, validating);
    if (!returns && !clean && sinks.isEmpty() && validated.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new MethodSummary(
            returns,
            sinks,
            clean,
            validated,
            returned.map(Taint::assumptions).orElse(List.of()),
            SinkParameters.assumptions(sinkFlows)
        )
    );
  }
}
