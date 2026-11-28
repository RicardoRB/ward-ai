package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the taint analysis reads from one graph of another file that depends on the graph alone:
 * who the method is, which calls it makes, its place in the type hierarchy, the types it binds and
 * its hash. The facts of a graph are read once per run and shared by every file that reaches it.
 *
 * @param local the method the body declares, or empty
 * @param external the method as another file sees it, or empty when the body has no owner
 * @param subtype the supertype fact the body records, or empty
 * @param bound the annotated parameter types of the graph, by annotation
 * @param callKeys the {@code name/arity} keys of the calls of the graph
 * @param hash the hash of the graph
 */
record GraphFacts(
    Optional<LocalMethod> local,
    Optional<LocalMethod> external,
    Optional<Implementors.Subtype> subtype,
    Map<String,
    Set<String>> bound,
    Set<String> callKeys,
    int hash
) {
  /** Reads the facts of a graph. */
  static GraphFacts of(final ControlFlowGraph graph) {
    final Optional<LocalMethod> local = LocalMethod.parse(graph);
    return new GraphFacts(
        local,
        local.filter(method -> !method.owner().isEmpty()).map(LocalMethod::asExternal),
        Implementors.subtypeOf(graph),
        BoundGetters.typesOf(List.of(graph), List.of()),
        CalledMethods.callKeys(List.of(graph)),
        graph.hashCode()
    );
  }
}
