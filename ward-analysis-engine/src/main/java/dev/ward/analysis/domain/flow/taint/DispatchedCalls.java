package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Names the class of a call's receiver, so a call through a variable, a field or a parameter
 * reaches the method of that class, as {@code Owner.m(x)} does. The class is the one the receiver
 * was created as, when the frontend recorded it as a {@value #CREATED_AS} annotation of the call,
 * and otherwise the declared type of the receiver. A call is renamed only when that class declares
 * a body for the method that the project's graphs know and no source, sink or sanitizer entry of
 * the specification already matches the call, so calls to library types keep their own matching.
 * The owner carries the package, so two classes with one simple name in different packages are told
 * apart.
 *
 * <p>The policy for a receiver whose class cannot be decided is to follow nothing: an interface or
 * abstract method has no body, so a call through one is followed only when the receiver was created
 * as a class. Subclasses that override the method of a declared class are not followed.
 */
final class DispatchedCalls {
  /** The prefix of the annotation that gives a call the fully qualified type of its creation. */
  static final String CREATED_AS = "ward:created-as:";

  private DispatchedCalls() {}

  /** Returns the graphs with the qualifier of each decidable call set to the owner's name. */
  static List<ControlFlowGraph> apply(
      final List<ControlFlowGraph> graphs,
      final Set<String> known,
      final Implementors implementors,
      final TaintSpec spec
  ) {
    return graphs.stream().map(graph -> apply(graph, known, implementors, spec)).toList();
  }

  /**
   * Returns the graph with the qualifier of each decidable call set to the owner's name; the graph
   * itself when no call changes, which is the usual case, so nothing is rebuilt for it.
   */
  static ControlFlowGraph apply(
      final ControlFlowGraph graph,
      final Set<String> known,
      final Implementors implementors,
      final TaintSpec spec
  ) {
    final List<BasicBlock> blocks = new ArrayList<>(graph.blocks().size());
    boolean changed = false;
    for (final BasicBlock block : graph.blocks()) {
      final BasicBlock applied = apply(block, known, implementors, spec);
      changed |= applied != block;
      blocks.add(applied);
    }
    return changed
        ? new ControlFlowGraph(graph.body(), graph.span(), blocks, graph.entry(), graph.exit())
        : graph;
  }

  private static BasicBlock apply(
      final BasicBlock block,
      final Set<String> known,
      final Implementors implementors,
      final TaintSpec spec
  ) {
    final List<FlowSite> sites = new ArrayList<>(block.sites().size());
    boolean changed = false;
    for (final FlowSite site : block.sites()) {
      final FlowSite resolved = resolved(site, known, implementors, spec);
      changed |= resolved != site;
      sites.add(resolved);
    }
    return changed
        ? new BasicBlock(block.id(), block.nodes(), block.successors(), block.accesses(), sites)
        : block;
  }

  private static FlowSite resolved(
      final FlowSite site,
      final Set<String> known,
      final Implementors implementors,
      final TaintSpec spec
  ) {
    if (!(site.kind() == FlowSiteKind.CALL || site.kind() == FlowSiteKind.VALIDATED)
        || site.qualifier().isEmpty() && site.receiverType().isEmpty()
        || TaintSpans.matched(site, spec)) {
      return site;
    }
    final Optional<String> owner =
        site.qualifier().isEmpty()
            ? site.receiverType().filter(type -> known.contains(type + "#" + callKey(site)))
            : ownerOf(site, known, implementors);
    return owner.map(found -> named(site, found)).orElse(site);
  }

  /**
   * The owner that the call reaches: the type of the receiver with its package, or else its simple
   * name, which only a body without a package (Kotlin, Node) can have as its owner.
   */
  private static Optional<String> ownerOf(
      final FlowSite site,
      final Set<String> known,
      final Implementors implementors
  ) {
    final Optional<String> direct = directOwner(site, known);
    return direct.isPresent()
        ? direct
        : implementors.only(site.receiverType(), known, callKey(site));
  }

  private static Optional<String> directOwner(final FlowSite site, final Set<String> known) {
    final Optional<String> type = createdAs(site).or(site::receiverType);
    if (type.isEmpty()) {
      return Optional.empty();
    }
    final String suffix = "#" + callKey(site);
    final String full = type.get();
    if (known.contains(full + suffix)) {
      return Optional.of(full);
    }
    final String simple = simpleName(full);
    return known.contains(simple + suffix) ? Optional.of(simple) : Optional.empty();
  }

  private static Optional<String> createdAs(final FlowSite site) {
    for (final String annotation : site.annotations()) {
      if (annotation.startsWith(CREATED_AS)) {
        return Optional.of(annotation.substring(CREATED_AS.length()));
      }
    }
    return Optional.empty();
  }

  private static String simpleName(final String type) {
    return type.substring(type.lastIndexOf('.') + 1);
  }

  private static String callKey(final FlowSite site) {
    return site.kind() == FlowSiteKind.VALIDATED
        ? LocalMethod.key(ValidatedUses.function(site), ValidatedUses.arity(site))
        : LocalMethod.key(site.name(), site.arguments().size());
  }

  private static FlowSite named(final FlowSite site, final String owner) {
    return new FlowSite(
        site.kind(),
        site.name(),
        owner,
        site.receiverType(),
        site.annotations(),
        site.arguments(),
        site.span()
    );
  }
}
