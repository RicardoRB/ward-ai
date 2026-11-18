package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.BasicBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Follows a tainted value through the fields of one class: a field that a method of the class
 * assigns a tainted value is a source where the other methods of that class read it. The model is
 * flow-insensitive across methods, so the order in which the methods run is ignored, and it is per
 * class: only reads of the same owner, as {@code f} or {@code this.f}, see the taint. A field of
 * another class, a static field read through its class name, and a getter are not followed.
 */
final class TaintFields {
  private static final String THIS = "this";
  private static final int MAX_ROUNDS = 4;

  private TaintFields() {}

  /** The tainted fields of each owner, found by repeating the analysis until none is added. */
  static Map<String, Set<String>> of(final List<ControlFlowGraph> graphs, final TaintSpec spec) {
    final Map<String, Set<String>> fields = new HashMap<>();
    for (int round = 0; round < MAX_ROUNDS; round++) {
      boolean grown = false;
      for (final ControlFlowGraph graph : graphs) {
        grown |= addStored(graph, spec, fields);
      }
      if (!grown) {
        break;
      }
    }
    return fields;
  }

  private static boolean addStored(
      final ControlFlowGraph graph,
      final TaintSpec spec,
      final Map<String,
      Set<String>> fields
  ) {
    final Optional<String> owner = LocalMethod.ownerOf(graph);
    if (owner.isEmpty()) {
      return false;
    }
    final Set<String> stored =
        storedFields(graph, specFor(graph, spec, fields));
    return fields.computeIfAbsent(owner.get(), key -> new HashSet<>()).addAll(stored);
  }

  /**
   * Finds the fields of {@code this} that the graph assigns a tainted value.
   *
   * @param graph the control-flow graph of a function
   * @param spec the taint specification
   * @return the names of the fields, in block order
   */
  private static Set<String> storedFields(final ControlFlowGraph graph, final TaintSpec spec) {
    final Set<String> fields = new LinkedHashSet<>();
    if (!TaintSpans.hasSource(graph, spec)) {
      return fields;
    }
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);
    final Map<AccessSite, Taint> origins = TaintOrigins.of(graph, reaching, spec);
    for (final BasicBlock block : graph.blocks()) {
      block.sites().stream()
          .filter(site -> site.kind() == FlowSiteKind.MEMBER_WRITE)
          .filter(site -> THIS.equals(site.qualifier()) && site.arguments().size() == 1)
          .filter(
              site ->
                  TaintAnalysis.taintOf(
                      block,
                      site.arguments().get(0),
                      reaching,
                      origins,
                      spec
                  ).isPresent()
          )
          .forEach(site -> fields.add(site.name()));
    }
    return fields;
  }

  /** The specification with a source entry for each tainted field of the graph's owner. */
  static TaintSpec specFor(
      final ControlFlowGraph graph,
      final TaintSpec spec,
      final Map<String,
      Set<String>> fields
  ) {
    final Set<String> tainted =
        LocalMethod.ownerOf(graph).map(owner -> fields.getOrDefault(owner, Set.of())).orElse(
            Set.of()
        );
    if (tainted.isEmpty()) {
      return spec;
    }
    final List<TaintEntry> sources = new ArrayList<>(spec.sources());
    tainted.stream().sorted().map(TaintFields::entry).forEach(sources::add);
    return new TaintSpec(sources, spec.sinks(), spec.sanitizers(), spec.propagators());
  }

  private static TaintEntry entry(final String field) {
    return new TaintEntry(
        FlowSiteKind.MEMBER_READ,
        field,
        Optional.empty(),
        Optional.of(THIS),
        List.of()
    );
  }
}
