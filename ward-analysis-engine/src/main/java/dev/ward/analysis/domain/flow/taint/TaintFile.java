package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Taint analysis over all the bodies of one file, so that a value can cross a method boundary
 * inside the file. A method that returns a value from a source is itself a source for its callers,
 * and a method whose parameter reaches a sink is itself a sink for that argument of its callers.
 * The summaries are found by repeating the intra-procedural analysis with the summaries of the
 * previous round added to the specification, until they stop changing or {@value #MAX_ROUNDS}
 * rounds have run, which also bounds recursion.
 *
 * <p>A finding that crosses a call is reported at the call, in the caller, and its path ends at the
 * call. Calls to methods of other files, fields shared between methods, and overloads of one arity
 * are not followed. Only this file's graphs are read, so a cached result of the file stays valid
 * while its text and the specification are unchanged.
 */
public final class TaintFile {

  private static final Logger LOG = Logger.getLogger(TaintFile.class.getName());
  private static final int MAX_ROUNDS = 4;

  /** What the callers of one method need to know about it. */
  private record Summary(boolean returnsTaint, Set<Integer> sinkParameters) {}

  private TaintFile() {}

  /**
   * Runs the analysis on every graph of a file.
   *
   * @param graphs the control-flow graphs of the file, in source order
   * @param spec the taint specification
   * @return the findings of all graphs, graph by graph in the order given
   */
  public static List<TaintFinding> run(final List<ControlFlowGraph> graphs, final TaintSpec spec) {
    final Map<String, LocalMethod> methods = called(graphs, LocalMethod.index(graphs));
    TaintSpec effective = spec;
    if (!methods.isEmpty()) {
      effective = summarized(spec, methods);
    }
    final List<TaintFinding> found = new ArrayList<>();
    for (final ControlFlowGraph graph : graphs) {
      found.addAll(TaintAnalysis.run(graph, effective));
    }
    return found;
  }

  /** Keeps the methods that some call of the file names with the right argument count. */
  private static Map<String, LocalMethod> called(
      final List<ControlFlowGraph> graphs, final Map<String, LocalMethod> methods) {
    final Set<String> calls = new HashSet<>();
    for (final ControlFlowGraph graph : graphs) {
      for (final BasicBlock block : graph.blocks()) {
        block.sites().stream()
            .filter(site -> site.kind() == FlowSiteKind.CALL)
            .forEach(site -> calls.add(callKey(site)));
      }
    }
    final Map<String, LocalMethod> kept = new HashMap<>(methods);
    kept.keySet().retainAll(calls);
    return kept;
  }

  private static String callKey(final FlowSite site) {
    return LocalMethod.key(site.name(), site.arguments().size());
  }

  private static TaintSpec summarized(
      final TaintSpec spec, final Map<String, LocalMethod> methods) {
    Map<String, Summary> known = Map.of();
    for (int round = 0; round < MAX_ROUNDS; round++) {
      final Map<String, Summary> next = summarize(derived(spec, methods, known), methods);
      if (next.equals(known)) {
        break;
      }
      known = next;
    }
    LOG.log(Level.FINE, "taint summaries for {0} local methods", methods.size());
    return derived(spec, methods, known);
  }

  private static Map<String, Summary> summarize(
      final TaintSpec spec, final Map<String, LocalMethod> methods) {
    final Map<String, Summary> found = new HashMap<>();
    final TaintSpec seeded = withParameterSource(spec);
    for (final LocalMethod method : methods.values()) {
      final boolean returns = TaintAnalysis.returned(method.graph(), spec).isPresent();
      final Set<Integer> sinks = sinkParameters(method, seeded);
      if (returns || !sinks.isEmpty()) {
        found.put(method.key(), new Summary(returns, sinks));
      }
    }
    return found;
  }

  private static Set<Integer> sinkParameters(final LocalMethod method, final TaintSpec seeded) {
    final Set<Integer> sinks = new TreeSet<>();
    final List<String> parameters = method.parameters();
    if (parameters.size() != method.arity()) {
      return sinks;
    }
    for (int index = 0; index < parameters.size(); index++) {
      final ControlFlowGraph marked = method.withTaintedParameter(parameters.get(index));
      if (TaintAnalysis.run(marked, seeded).stream()
          .anyMatch(finding -> LocalMethod.mark().equals(finding.source()))) {
        sinks.add(index);
      }
    }
    return sinks;
  }

  private static TaintSpec withParameterSource(final TaintSpec spec) {
    final List<TaintEntry> sources = new ArrayList<>(spec.sources());
    sources.add(
        new TaintEntry(
            FlowSiteKind.PARAMETER,
            LocalMethod.mark(),
            Optional.empty(),
            Optional.empty(),
            List.of()));
    return new TaintSpec(sources, spec.sinks(), spec.sanitizers(), spec.propagators());
  }

  /** The specification plus a source or sink entry per summarized method, for each qualifier. */
  private static TaintSpec derived(
      final TaintSpec spec,
      final Map<String, LocalMethod> methods,
      final Map<String, Summary> summaries) {
    final List<TaintEntry> sources = new ArrayList<>(spec.sources());
    final List<TaintEntry> sinks = new ArrayList<>(spec.sinks());
    summaries.forEach(
        (key, summary) -> {
          final LocalMethod method = methods.get(key);
          for (final String qualifier : method.qualifiers()) {
            if (summary.returnsTaint()) {
              sources.add(entry(method, qualifier, List.of()));
            }
            if (!summary.sinkParameters().isEmpty()) {
              sinks.add(entry(method, qualifier, List.copyOf(summary.sinkParameters())));
            }
          }
        });
    return new TaintSpec(sources, sinks, spec.sanitizers(), spec.propagators());
  }

  private static TaintEntry entry(
      final LocalMethod method, final String qualifier, final List<Integer> arguments) {
    return new TaintEntry(
        FlowSiteKind.CALL, method.name(), Optional.empty(), Optional.of(qualifier), arguments);
  }
}
