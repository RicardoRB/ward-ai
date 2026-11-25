package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
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

  private TaintFile() {}

  /**
   * Runs the analysis on every graph of a file.
   *
   * @param graphs the control-flow graphs of the file, in source order
   * @param spec the taint specification
   * @return the findings of all graphs, graph by graph in the order given
   */
  public static List<TaintFinding> run(final List<ControlFlowGraph> graphs, final TaintSpec spec) {
    return run(graphs, List.of(), spec);
  }

  /**
   * Runs the analysis on every graph of a file, following calls into the graphs of the files it
   * depends on. A call reaches another file's method when it names the owner type, as in {@code
   * Owner.m(x)} or {@code (new Owner()).m(x)}; only the findings of {@code graphs} are returned.
   *
   * @param graphs the control-flow graphs of the file, in source order
   * @param related the graphs of the files it depends on, directly or not
   * @param spec the taint specification
   * @return the findings of all graphs, graph by graph in the order given
   */
  public static List<TaintFinding> run(
      final List<ControlFlowGraph> graphs,
      final List<ControlFlowGraph> related,
      final TaintSpec spec
  ) {
    final Prepared prepared = prepare(TaintContext.of(graphs, related), graphs, spec);
    final Map<String, Set<String>> fields = TaintFields.of(prepared.own(), prepared.effective());
    final List<TaintFinding> found = new ArrayList<>();
    for (final ControlFlowGraph graph : prepared.own()) {
      found.addAll(
          TaintAnalysis.run(graph, TaintFields.specFor(graph, prepared.effective(), fields))
      );
    }
    return found;
  }

  /** The graphs of the file with their calls resolved, and the specification they run with. */
  record Prepared(List<ControlFlowGraph> own, TaintSpec effective) {}

  static Prepared prepare(
      final TaintContext context,
      final List<ControlFlowGraph> graphs,
      final TaintSpec declared
  ) {
    final TaintSpec spec = BoundGetters.extend(declared, context.bound());
    final List<ControlFlowGraph> dispatched =
        DispatchedCalls.apply(graphs, context.known(), context.implementors(), spec);
    final Map<String, LocalMethod> methods = methodsOf(dispatched, context, spec);
    final UnfollowedCalls unfollowed = UnfollowedCalls.of(context, methods.values(), spec);
    if (methods.isEmpty()) {
      return new Prepared(dispatched.stream().map(unfollowed::apply).toList(), spec);
    }
    final UnaryOperator<ControlFlowGraph> checks = GuardedCalls.over(methods);
    final UnaryOperator<ControlFlowGraph> types = TypedCalls.over(methods);
    final UnaryOperator<ControlFlowGraph> guards = graph -> types.apply(checks.apply(graph));
    methods.replaceAll(
        (key, method) -> method.withGraph(unfollowed.apply(guards.apply(method.graph())))
    );
    final List<ControlFlowGraph> own =
        dispatched.stream().map(guards).map(unfollowed::apply).toList();
    return new Prepared(own, summarized(spec, methods, own));
  }

  /** The methods of the file and the reachable ones of other files, with their calls resolved. */
  private static Map<String, LocalMethod> methodsOf(
      final List<ControlFlowGraph> own,
      final TaintContext context,
      final TaintSpec spec
  ) {
    final Map<String, LocalMethod> methods = CalledMethods.local(own, LocalMethod.index(own));
    CalledMethods.reachable(own, context.index())
        .forEach(
            (key, method) ->
                methods.put(
                    key,
                    method.withGraph(
                        DispatchedCalls.apply(
                            method.graph(),
                            context.known(),
                            context.implementors(),
                            spec
                        )
                    )
                )
        );
    return methods;
  }

  private static TaintSpec summarized(
      final TaintSpec spec,
      final Map<String,
      LocalMethod> methods,
      final List<ControlFlowGraph> own
  ) {
    if (!TaintRelevance.anySite(methods.values(), spec)
        && !TaintRelevance.mayFind(own, spec)) {
      return spec;
    }
    Map<String, MethodSummary> known = Map.of();
    for (int round = 0; round < MAX_ROUNDS; round++) {
      final Map<String, MethodSummary> next = summarize(
          derived(spec, methods, known),
          methods,
          known
      );
      if (next.equals(known)) {
        break;
      }
      known = next;
    }
    if (LOG.isLoggable(Level.FINE)) {
      LOG.log(Level.FINE, "taint summaries for {0} local methods", methods.size());
    }
    return derived(spec, methods, known);
  }

  private static Map<String, MethodSummary> summarize(
      final TaintSpec spec,
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      MethodSummary> previous
  ) {
    final Map<String, MethodSummary> found = new HashMap<>();
    final TaintSpec seeded = withParameterSource(spec);
    final Map<String, Map<String, Set<Integer>>> validating =
        previous.entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().validated())
        );
    for (final LocalMethod method : methods.values()) {
      MethodSummary.of(method, spec, seeded, methods, validating)
          .ifPresent(summary -> found.put(method.key(), summary));
    }
    return found;
  }

  private static TaintSpec withParameterSource(final TaintSpec spec) {
    final List<TaintEntry> sources = new ArrayList<>(spec.sources());
    sources.add(
        new TaintEntry(
            FlowSiteKind.PARAMETER,
            LocalMethod.mark(),
            Optional.empty(),
            Optional.empty(),
            List.of()
        )
    );
    return new TaintSpec(sources, spec.sinks(), spec.sanitizers(), spec.propagators());
  }

  /** The specification plus a source or sink entry per summarized method, for each qualifier. */
  private static TaintSpec derived(
      final TaintSpec spec,
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      MethodSummary> summaries
  ) {
    final List<TaintEntry> sources = new ArrayList<>(spec.sources());
    final List<TaintEntry> sinks = new ArrayList<>(spec.sinks());
    final List<TaintEntry> sanitizers = new ArrayList<>(spec.sanitizers());
    summaries.forEach(
        (key, summary) -> {
          final LocalMethod method = methods.get(key);
          for (final String qualifier : method.qualifiers()) {
            if (summary.returnsTaint()) {
              sources.add(
                  entry(method, qualifier, List.of()).assuming(summary.returnAssumptions())
              );
            }
            if (summary.cleanResult()) {
              sanitizers.add(entry(method, qualifier, List.of()));
            }
            if (!summary.sinkParameters().isEmpty()) {
              sinks.add(
                  entry(method, qualifier, List.copyOf(summary.sinkParameters()))
                      .assuming(summary.sinkAssumptions())
              );
            }
            summary.validated().forEach(
                (what, parameters) -> parameters.forEach(
                    index -> sanitizers.add(ValidatedUses.use(method, qualifier, what, index))
                )
            );
          }
        }
    );
    return new TaintSpec(sources, sinks, sanitizers, spec.propagators());
  }

  private static TaintEntry entry(
      final LocalMethod method,
      final String qualifier,
      final List<Integer> arguments
  ) {
    return new TaintEntry(
        FlowSiteKind.CALL,
        method.name(),
        Optional.empty(),
        Optional.of(qualifier),
        arguments
    );
  }
}
