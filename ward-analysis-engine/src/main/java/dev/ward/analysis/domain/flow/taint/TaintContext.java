package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * @param declared the {@code name/arity} keys of every body of the file and of other files
 */
record TaintContext(
    Set<String> known,
    Map<String,
    LocalMethod> index,
    Implementors implementors,
    Set<String> declared
) {
  /** Reads the context of a file with its own graphs and those of the files it depends on. */
  static TaintContext of(
      final List<ControlFlowGraph> graphs,
      final List<ControlFlowGraph> related
  ) {
    final Set<String> known = new HashSet<>(LocalMethod.owned(graphs));
    final List<LocalMethod> externals = LocalMethod.externals(related);
    for (final LocalMethod external : externals) {
      known.add(external.key());
    }
    return new TaintContext(
        known,
        LocalMethod.indexExternal(externals),
        Implementors.of(Stream.concat(graphs.stream(), related.stream()).toList()),
        LocalMethod.declared(Stream.concat(graphs.stream(), related.stream()).toList())
    );
  }
}
