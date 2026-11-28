package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The classes of the project that extend or implement a type, read from the supertype graphs that
 * a frontend records as bodies named {@code scope#ward:super:<type>}. A call on a receiver of an
 * interface or abstract type is followed only when exactly one class of the project that declares
 * a body for the method is a subtype of the receiver's type, directly or through other types.
 */
final class Implementors {
  private static final String MARK = "ward:super:";
  private static final char PACKAGE_END = ':';
  private static final char SCOPE_END = '#';

  private final Map<String, Set<String>> subtypes;

  private Implementors(final Map<String, Set<String>> subtypes) {
    this.subtypes = subtypes;
  }

  /** A class and the type it extends or implements. */
  record Subtype(String type, String sub) {}

  /** Reads the supertype facts of the graphs. */
  static Implementors of(final List<ControlFlowGraph> graphs) {
    return fromSubtypes(graphs.stream().flatMap(graph -> subtypeOf(graph).stream()).toList());
  }

  /** The supertype fact that a graph records, or empty. */
  static Optional<Subtype> subtypeOf(final ControlFlowGraph graph) {
    final int mark = graph.body().indexOf(SCOPE_END + MARK);
    if (mark <= 0) {
      return Optional.empty();
    }
    return Optional.of(
        new Subtype(
            graph.body().substring(mark + 1 + MARK.length()),
            owner(graph.body().substring(0, mark))
        )
    );
  }

  /** Arranges the supertype facts. */
  static Implementors fromSubtypes(final List<Subtype> facts) {
    final Map<String, Set<String>> direct = new HashMap<>();
    for (final Subtype fact : facts) {
      add(direct, fact.type(), fact.sub());
    }
    return new Implementors(direct);
  }

  private static void add(
      final Map<String,
      Set<String>> direct,
      final String type,
      final String sub
  ) {
    direct.computeIfAbsent(type, key -> new HashSet<>()).add(sub);
  }

  /**
   * The one subtype of the type that has a body for the call key, or empty when there is none or
   * several.
   */
  Optional<String> only(final Optional<String> type, final Set<String> known, final String key) {
    final List<String> owners =
        type.stream()
            .flatMap(name -> of(name).stream())
            .filter(owner -> known.contains(owner + "#" + key))
            .sorted()
            .toList();
    return owners.size() == 1 ? Optional.of(owners.get(0)) : Optional.empty();
  }

  /** The classes that are subtypes of the type, directly or not, without the type itself. */
  Set<String> of(final String type) {
    final Set<String> found = new HashSet<>();
    final List<String> pending = new ArrayList<>(List.of(type));
    while (!pending.isEmpty()) {
      final String next = pending.remove(pending.size() - 1);
      for (final String sub : subtypes.getOrDefault(next, Set.of())) {
        if (found.add(sub)) {
          pending.add(sub);
        }
      }
    }
    return found;
  }

  private static String owner(final String scope) {
    final int colon = scope.indexOf(PACKAGE_END);
    final String chain = scope.substring(colon + 1);
    return colon < 0 || chain.isEmpty() ? chain : scope.substring(0, colon) + "." + chain;
  }
}
