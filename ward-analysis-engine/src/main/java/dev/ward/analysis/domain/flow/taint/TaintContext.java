package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * What the taint analysis of a file reads from the files around it that does not depend on the
 * specification: the keys of the methods that calls can name, the index of the methods of other
 * files and the classes that implement each type. It is computed once per file, so the analysis of
 * a file for many specifications shares it.
 *
 * @param known the {@code Owner#name/arity} keys of the methods of the file and of other files
 * @param index the methods of other files, by key
 * @param implementors the subtypes of the types the graphs record
 * @param bound the types of the annotated parameters of the graphs, by annotation
 * @param declared the {@code name/arity} keys of every body of the file and of other files
 * @param bound the types of the annotated parameters of the graphs, by annotation
 * @param universe every graph of the file and of the files around it
 * @param memo the results that files of one run share
 */
record TaintContext(
    Set<String> known,
    Map<String,
    LocalMethod> index,
    Implementors implementors,
    Set<String> declared,
    Map<String,
    Set<String>> bound,
    List<ControlFlowGraph> universe,
    SummaryMemo memo
) {
  /** Reads the context of a file with its own graphs and those of the files it depends on. */
  static TaintContext of(
      final List<ControlFlowGraph> graphs,
      final List<ControlFlowGraph> related
  ) {
    return of(graphs, related, SummaryMemo.disabled());
  }

  /** Reads the context of a file, sharing method results through {@code memo}. */
  static TaintContext of(
      final List<ControlFlowGraph> graphs,
      final List<ControlFlowGraph> related,
      final SummaryMemo memo
  ) {
    final Set<String> known = new HashSet<>();
    final Set<String> declared = new HashSet<>();
    final List<LocalMethod> externals = new ArrayList<>();
    final List<Implementors.Subtype> subtypes = new ArrayList<>();
    final Map<String, Set<String>> bound = new TreeMap<>();
    for (final ControlFlowGraph graph : graphs) {
      final GraphFacts facts = memo.facts(graph);
      facts.local()
          .filter(method -> !method.owner().isEmpty())
          .ifPresent(method -> known.add(method.owner() + "#" + method.nameKey()));
      read(facts, declared, subtypes, bound);
    }
    for (final ControlFlowGraph graph : related) {
      final GraphFacts facts = memo.facts(graph);
      facts.external().ifPresent(externals::add);
      read(facts, declared, subtypes, bound);
    }
    for (final LocalMethod external : externals) {
      known.add(external.key());
    }
    return new TaintContext(
        known,
        LocalMethod.indexExternal(externals),
        Implementors.fromSubtypes(subtypes),
        declared,
        bound,
        Stream.concat(graphs.stream(), related.stream()).toList(),
        memo
    );
  }

  private static void read(
      final GraphFacts facts,
      final Set<String> declared,
      final List<Implementors.Subtype> subtypes,
      final Map<String,
      Set<String>> bound
  ) {
    facts.local().ifPresent(method -> declared.add(method.nameKey()));
    facts.subtype().ifPresent(subtypes::add);
    facts.bound().forEach(
        (annotation, types) -> bound.computeIfAbsent(annotation, key -> new TreeSet<>())
            .addAll(types)
    );
  }
}
