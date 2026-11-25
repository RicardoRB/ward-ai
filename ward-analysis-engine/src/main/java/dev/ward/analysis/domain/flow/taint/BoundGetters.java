package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The getters of the types that a request handler binds from the request. A parameter source entry
 * whose qualifier is {@code getters} names an annotation (such as {@code @RequestBody}); every
 * parameter of the graphs of the file and of the files around it that carries that annotation and
 * has a resolved type makes the getters of that type sources. The value a getter returns is only
 * assumed to be what a request set, since the entity may have been stored and loaded again, so the
 * source carries an assumption and its findings are {@code possible}. Where the entity is the
 * handler parameter itself the flow is proven by the parameter source, which wins.
 */
final class BoundGetters {
  /** The qualifier of a parameter source entry whose type's getters are sources too. */
  static final String QUALIFIER = "getters";

  private static final String GETTERS = "get*";

  private BoundGetters() {}

  /** The annotated parameter types of the graphs, by annotation, sorted. */
  static Map<String, Set<String>> typesOf(
      final List<ControlFlowGraph> graphs,
      final List<ControlFlowGraph> related
  ) {
    final Map<String, Set<String>> found = new TreeMap<>();
    Stream.concat(graphs.stream(), related.stream())
        .flatMap(graph -> graph.blocks().stream())
        .flatMap(block -> block.sites().stream())
        .filter(site -> site.kind() == FlowSiteKind.PARAMETER && site.receiverType().isPresent())
        .forEach(
            site -> site.annotations().forEach(
                annotation -> found.computeIfAbsent(annotation, key -> new TreeSet<>())
                    .add(site.receiverType().orElseThrow())
            )
        );
    return found;
  }

  /** The specification with the getter sources of the bound types added after its own sources. */
  static TaintSpec extend(final TaintSpec spec, final Map<String, Set<String>> bound) {
    if (bound.isEmpty()) {
      return spec;
    }
    final List<TaintEntry> sources = new ArrayList<>(spec.sources());
    for (final TaintEntry entry : spec.sources()) {
      if (isBinding(entry)) {
        bound.getOrDefault(entry.name(), Set.of()).forEach(type -> sources.add(getters(type)));
      }
    }
    return sources.size() == spec.sources().size()
        ? spec
        : new TaintSpec(sources, spec.sinks(), spec.sanitizers(), spec.propagators());
  }

  private static boolean isBinding(final TaintEntry entry) {
    return entry.kind() == FlowSiteKind.PARAMETER
        && entry.qualifier().filter(QUALIFIER::equals).isPresent();
  }

  private static TaintEntry getters(final String type) {
    return new TaintEntry(
        FlowSiteKind.CALL,
        GETTERS,
        Optional.of(type),
        Optional.empty(),
        List.of()
    ).assuming(List.of("a getter of " + type + " is assumed to return what a request set"));
  }
}
