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

  /** The summary of a method, or empty when its callers need to know nothing about it. */
  static Optional<MethodSummary> of(
      final LocalMethod method,
      final TaintSpec spec,
      final TaintSpec seeded,
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      Map<String,
      Set<Integer>>> validating
  ) {
    final Optional<Taint> returned = TaintAnalysis.returned(method.graph(), spec);
    final boolean returns = returned.isPresent();
    final Map<Integer, List<String>> sinkFlows = SinkParameters.of(method, seeded);
    final Set<Integer> sinks = new TreeSet<>(sinkFlows.keySet());
    final boolean clean = !returns && sinks.isEmpty() && CleanResults.of(method, seeded);
    final Map<String, Set<Integer>> validated =
        ValidatingMethods.of(method, spec, methods, validating);
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
