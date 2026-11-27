package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Lets a null check vouch for the key of a lookup. A method that gives a non-null value only when
 * its parameter is a key of a collection (a body that returns inside {@code if (C.has(p))} and
 * otherwise {@code null}) says so to its callers when {@link MemberCollections} proves that the
 * collection holds only keys of the program. The caller's frontend marks the check that exits when
 * {@code x} is null, for {@code x = await f(k)}, with a site named {@value #NULL_CHECKED}: its
 * qualifier is {@code k}, its annotations the called name and {@code x}. When {@code f} is such a
 * method, {@code k} is written clean right after the check, as {@link GuardedCalls} does for a
 * check in the callee.
 */
final class MemberLookups {
  /** The name of the site that marks a return of a body. */
  static final String RETURNED = "<returned>";

  /** The name of the site that marks a null check of the result of a call. */
  static final String NULL_CHECKED = "<null-checked>";

  private MemberLookups() {}

  /**
   * Returns what puts a clean write after each null check that a method proves a membership for;
   * the identity when no body has such a check.
   */
  static UnaryOperator<ControlFlowGraph> over(
      final Map<String,
      LocalMethod> methods,
      final List<ControlFlowGraph> own,
      final List<ControlFlowGraph> universe,
      final TaintSpec spec
  ) {
    if (own.stream().noneMatch(MemberLookups::marked)
        && methods.values().stream().noneMatch(method -> marked(method.graph()))) {
      return graph -> graph;
    }
    final Map<String, Boolean> proved = new HashMap<>();
    return graph -> apply(
        graph,
        methods,
        key -> proved.computeIfAbsent(
            key,
            name -> MemberCollections.holds(methods.get(name), methods, universe, spec)
        )
    );
  }

  /** Whether a graph has a check that a membership or a listing may vouch for. */
  static boolean marked(final ControlFlowGraph graph) {
    return nullChecked(graph) || ListingMarks.checked(graph);
  }

  /** Whether a graph has a null check that a membership may vouch for. */
  static boolean nullChecked(final ControlFlowGraph graph) {
    return graph.blocks().stream()
        .flatMap(block -> block.sites().stream())
        .anyMatch(site -> NULL_CHECKED.equals(site.name()));
  }

  private static ControlFlowGraph apply(
      final ControlFlowGraph graph,
      final Map<String,
      LocalMethod> methods,
      final Predicate<String> proved
  ) {
    final List<BasicBlock> blocks =
        graph.blocks().stream().map(block -> rewritten(graph, block, methods, proved)).toList();
    return blocks.equals(graph.blocks())
        ? graph
        : new ControlFlowGraph(graph.body(), graph.span(), blocks, graph.entry(), graph.exit());
  }

  /** The block with a clean write at its start for each null check that a membership vouches for. */
  private static BasicBlock rewritten(
      final ControlFlowGraph graph,
      final BasicBlock block,
      final Map<String,
      LocalMethod> methods,
      final Predicate<String> proved
  ) {
    final List<VariableAccess> accesses = new ArrayList<>(block.accesses());
    for (final FlowSite site : block.sites()) {
      final Predicate<LocalMethod> vouches = vouching(site, proved);
      if (vouches != null) {
        clean(graph, site, methods, vouches).ifPresent(write -> accesses.add(0, write));
      }
    }
    return accesses.size() == block.accesses().size()
        ? block
        : new BasicBlock(block.id(), block.nodes(), block.successors(), accesses, block.sites());
  }

  /** What a method must prove for the check, or null when the site is no check. */
  private static Predicate<LocalMethod> vouching(
      final FlowSite site,
      final Predicate<String> proved
  ) {
    if (NULL_CHECKED.equals(site.name())) {
      return method -> proved.test(method.key());
    }
    if (ListingMarks.LENGTH_CHECKED.equals(site.name())) {
      return method -> ListingMarks.vouches(method, site.annotations().get(2));
    }
    return null;
  }

  /**
   * The clean write for the key of a null check, when {@code k} and {@code x} are assigned once and
   * every call of the checked name with {@code k} reaches a method that proves a membership.
   */
  private static Optional<VariableAccess> clean(
      final ControlFlowGraph graph,
      final FlowSite check,
      final Map<String,
      LocalMethod> methods,
      final Predicate<LocalMethod> proved
  ) {
    final String key = check.qualifier();
    final String callee = check.annotations().get(0);
    final String result = check.annotations().get(1);
    final List<VariableAccess> reads =
        graph.blocks().stream()
            .flatMap(block -> block.accesses().stream())
            .filter(access -> access.kind() == AccessKind.READ && access.name().equals(key))
            .toList();
    final List<FlowSite> calls =
        graph.blocks().stream()
            .flatMap(block -> block.sites().stream())
            .filter(site -> site.kind() == FlowSiteKind.CALL && site.name().equals(callee))
            .filter(site -> site.arguments().size() == 1)
            .filter(
                site -> reads.stream().anyMatch(read -> read.span().equals(site.arguments().get(0)))
            )
            .toList();
    if (calls.isEmpty()
        || assignments(graph, key) != 1
        || assignments(graph, result) != 1
        || !calls.stream().allMatch(call -> proves(call, methods, proved))) {
      return Optional.empty();
    }
    final Span argument = calls.get(0).arguments().get(0);
    return reads.stream()
        .filter(read -> read.span().equals(argument))
        .findFirst()
        .map(read -> new VariableAccess(key, AccessKind.WRITE, check.span(), read.declaration()));
  }

  private static boolean proves(
      final FlowSite call,
      final Map<String,
      LocalMethod> methods,
      final Predicate<LocalMethod> proved
  ) {
    final List<LocalMethod> reached =
        methods.values().stream().filter(method -> GuardedCalls.calls(method, call)).toList();
    return !reached.isEmpty() && reached.stream().allMatch(proved);
  }

  /** The number of places that give the variable a value: its writes and its parameter entry. */
  static long assignments(final ControlFlowGraph graph, final String name) {
    return graph.blocks().stream()
        .flatMap(block -> block.accesses().stream())
        .filter(access -> access.name().equals(name))
        .filter(
            access -> access.kind() == AccessKind.WRITE || access.kind() == AccessKind.PARAMETER
        )
        .count();
  }
}
