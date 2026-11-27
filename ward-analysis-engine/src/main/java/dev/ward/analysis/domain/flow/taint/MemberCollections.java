package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Proves that a method gives a non-null value only for a key of a collection that holds keys of
 * the program only. The method returns {@code null} on every path except inside {@code
 * if (C.has(p))}, where {@code p} is its one parameter and {@code C} a local assigned once from
 * {@code await getter()} and used only to call {@code has}, {@code get}, {@code keys}, {@code
 * values}, {@code entries} and {@code set} on. The getter returns the one collection {@code V} that
 * its file writes to; every call of the getter in the project assigns its result to a local used
 * the same way, so no code holds {@code V} but through those calls; and the key of every {@code
 * set} call through {@code V} or one of those locals carries no taint of the specification. The
 * frontend marks the returns of the bodies with {@value MemberLookups#RETURNED}.
 *
 * <p>shortcut: the callers of the getter are the calls named like it in the graphs given, and a
 * getter passed as a value or imported under another name is not followed; upgrade when a project
 * hands its collections out through function values.
 */
final class MemberCollections {
  private static final String SET = "set";
  private static final int ONE = 1;

  private MemberCollections() {}

  /** Whether {@code method} gives a value only for a key of a collection of keys of the program. */
  static boolean holds(
      final LocalMethod method,
      final Map<String,
      LocalMethod> methods,
      final List<ControlFlowGraph> universe,
      final TaintSpec spec
  ) {
    final Optional<MemberReturns.Member> member = MemberReturns.member(method);
    if (member.isEmpty() || method.arity() != ONE) {
      return false;
    }
    final Optional<LocalMethod> getter = getter(method, member.get().collection(), methods);
    final Optional<String> collection =
        getter.flatMap(found -> MemberReturns.returned(found.graph()));
    final Optional<Set<String>> aliases =
        collection.isPresent() ? callersHold(getter.get(), universe) : Optional.empty();
    if (aliases.isEmpty()) {
      return false;
    }
    final Set<String> written = new HashSet<>(aliases.get());
    written.add(collection.get());
    written.add(member.get().collection());
    return keysOfProgram(written, universe, spec);
  }

  /** The one method that the call initializing {@code local} reaches, when there is exactly one. */
  private static Optional<LocalMethod> getter(
      final LocalMethod method,
      final String local,
      final Map<String,
      LocalMethod> methods
  ) {
    final ControlFlowGraph graph = method.graph();
    final List<FlowSite> calls =
        graph.blocks().stream()
            .flatMap(block -> block.sites().stream())
            .filter(site -> site.kind() == FlowSiteKind.CALL && site.arguments().isEmpty())
            .filter(site -> CollectionLocals.initialized(graph, site).equals(Optional.of(local)))
            .toList();
    if (calls.size() != ONE) {
      return Optional.empty();
    }
    final List<LocalMethod> reached =
        methods.values().stream().filter(other -> GuardedCalls.calls(other, calls.get(0))).toList();
    return reached.size() == ONE ? Optional.of(reached.get(0)) : Optional.empty();
  }

  /**
   * The locals that every call of the getter in the graphs assigns its result to, when each call
   * does only that; empty when a call hands the collection on.
   */
  private static Optional<Set<String>> callersHold(
      final LocalMethod getter,
      final List<ControlFlowGraph> universe
  ) {
    final Set<String> aliases = new HashSet<>();
    for (final ControlFlowGraph graph : universe) {
      for (final BasicBlock block : graph.blocks()) {
        for (final FlowSite site : block.sites()) {
          if (site.kind() == FlowSiteKind.CALL && site.name().equals(getter.name())) {
            final Optional<String> alias = CollectionLocals.initialized(graph, site);
            if (alias.isEmpty()) {
              return Optional.empty();
            }
            aliases.add(alias.get());
          }
        }
      }
    }
    return Optional.of(aliases);
  }

  /**
   * Whether no key written through one of the names carries taint, in the graphs of the project:
   * the collection itself and the locals that hold it.
   */
  private static boolean keysOfProgram(
      final Set<String> names,
      final List<ControlFlowGraph> universe,
      final TaintSpec spec
  ) {
    final List<TaintEntry> writes =
        names.stream()
            .sorted()
            .map(
                name -> new TaintEntry(
                    FlowSiteKind.CALL,
                    SET,
                    Optional.empty(),
                    Optional.of(name),
                    List.of(0)
                )
            )
            .toList();
    final TaintSpec checked =
        new TaintSpec(spec.sources(), writes, spec.sanitizers(), spec.propagators());
    return TaintFile.prepare(TaintContext.of(universe, List.of()), universe, checked, false)
        .analyse()
        .isEmpty();
  }
}
