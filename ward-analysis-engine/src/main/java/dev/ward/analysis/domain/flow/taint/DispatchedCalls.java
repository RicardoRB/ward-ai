package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Names the class of a call's receiver, so a call through a variable, a field or a parameter
 * reaches the method of that class, as {@code Owner.m(x)} does. The class is the one the receiver
 * was created as, when the frontend recorded it as a {@value #CREATED_AS} annotation of the call,
 * and otherwise the declared type of the receiver. A call is renamed only when that class declares
 * a body for the method that the project's graphs know and no entry of the specification already
 * matches the call, so calls to library types keep their own matching.
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
      final List<ControlFlowGraph> graphs, final Set<String> known, final TaintSpec spec) {
    return graphs.stream().map(graph -> apply(graph, known, spec)).toList();
  }

  /** Returns the graph with the qualifier of each decidable call set to the owner's name. */
  static ControlFlowGraph apply(
      final ControlFlowGraph graph, final Set<String> known, final TaintSpec spec) {
    final List<BasicBlock> blocks =
        graph.blocks().stream().map(block -> apply(block, known, spec)).toList();
    return new ControlFlowGraph(graph.body(), graph.span(), blocks, graph.entry(), graph.exit());
  }

  private static BasicBlock apply(
      final BasicBlock block, final Set<String> known, final TaintSpec spec) {
    final List<FlowSite> sites =
        block.sites().stream().map(site -> resolved(site, known, spec)).toList();
    return new BasicBlock(block.id(), block.nodes(), block.successors(), block.accesses(), sites);
  }

  private static FlowSite resolved(
      final FlowSite site, final Set<String> known, final TaintSpec spec) {
    if (site.kind() != FlowSiteKind.CALL || site.qualifier().isEmpty() || matched(site, spec)) {
      return site;
    }
    return ownerOf(site)
        .filter(owner -> known.contains(owner + "#" + callKey(site)))
        .map(owner -> named(site, owner))
        .orElse(site);
  }

  private static Optional<String> ownerOf(final FlowSite site) {
    final Optional<String> created =
        site.annotations().stream()
            .filter(annotation -> annotation.startsWith(CREATED_AS))
            .map(annotation -> annotation.substring(CREATED_AS.length()))
            .findFirst();
    return created.or(site::receiverType).map(DispatchedCalls::simpleName);
  }

  private static String simpleName(final String type) {
    return type.substring(type.lastIndexOf('.') + 1);
  }

  private static String callKey(final FlowSite site) {
    return LocalMethod.key(site.name(), site.arguments().size());
  }

  private static boolean matched(final FlowSite site, final TaintSpec spec) {
    return Stream.of(spec.sources(), spec.sinks(), spec.sanitizers(), spec.propagators())
        .flatMap(List::stream)
        .anyMatch(entry -> entry.matches(site));
  }

  private static FlowSite named(final FlowSite site, final String owner) {
    return new FlowSite(
        site.kind(),
        site.name(),
        owner,
        site.receiverType(),
        site.annotations(),
        site.arguments(),
        site.span());
  }
}
