package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Marks the calls into project code that the analysis does not follow, so a value that passes
 * through one of them is an assumed step. A call is such a call when a body of the project has its
 * name and argument count, no source, sink, sanitizer or propagator entry matches it, no method that
 * the analysis reads is the one it names, and its receiver is a project type or unknown. That is a
 * call whose owner is ambiguous, such as an overload, a call through an interface with several
 * implementors, or a call on a receiver whose type is not known. The body may check or clean the
 * value, so the analysis cannot tell that the value it returns is still tainted.
 *
 * <p>A call to a library type keeps the usual rule that its result carries the taint of its
 * arguments, and is not marked.
 */
final class UnfollowedCalls {
  /** The annotation that a marked call carries. */
  static final String MARK = "ward:unfollowed";

  private final Set<String> declared;
  private final Set<String> owners;
  private final Map<String, List<LocalMethod>> followed;
  private final TaintSpec spec;

  private UnfollowedCalls(
      final Set<String> declared,
      final Set<String> owners,
      final Map<String,
      List<LocalMethod>> followed,
      final TaintSpec spec
  ) {
    this.declared = declared;
    this.owners = owners;
    this.followed = followed;
    this.spec = spec;
  }

  /**
   * Returns what marks the unfollowed calls of a graph.
   *
   * @param context the project around the file
   * @param methods the methods the analysis reads
   * @param spec the taint specification
   */
  static UnfollowedCalls of(
      final TaintContext context,
      final Collection<LocalMethod> methods,
      final TaintSpec spec
  ) {
    return new UnfollowedCalls(
        context.declared(),
        context.known().stream()
            .map(key -> key.substring(0, key.indexOf('#')))
            .flatMap(owner -> Stream.of(owner, simpleName(owner)))
            .collect(Collectors.toSet()),
        methods.stream()
            .collect(
                Collectors.groupingBy(method -> LocalMethod.key(method.name(), method.arity()))
            ),
        spec
    );
  }

  /** Returns the graph with each unfollowed call marked; the graph itself when none is. */
  ControlFlowGraph apply(final ControlFlowGraph graph) {
    final List<BasicBlock> blocks = new ArrayList<>(graph.blocks().size());
    boolean changed = false;
    for (final BasicBlock block : graph.blocks()) {
      final BasicBlock applied = apply(block);
      changed |= applied != block;
      blocks.add(applied);
    }
    return changed
        ? new ControlFlowGraph(graph.body(), graph.span(), blocks, graph.entry(), graph.exit())
        : graph;
  }

  private BasicBlock apply(final BasicBlock block) {
    final List<FlowSite> sites = new ArrayList<>(block.sites().size());
    boolean changed = false;
    for (final FlowSite site : block.sites()) {
      final boolean unfollowed = isUnfollowed(site);
      changed |= unfollowed;
      sites.add(unfollowed ? marked(site) : site);
    }
    return changed
        ? new BasicBlock(block.id(), block.nodes(), block.successors(), block.accesses(), sites)
        : block;
  }

  private boolean isUnfollowed(final FlowSite site) {
    final String key = LocalMethod.key(site.name(), site.arguments().size());
    return site.kind() == FlowSiteKind.CALL
        && !site.annotations().contains(MARK)
        && declared.contains(key)
        && site.receiverType().map(this::isProjectType).orElse(true)
        && !TaintSpans.matched(site, spec)
        && spec.propagators().stream().noneMatch(entry -> entry.matches(site))
        && followed.getOrDefault(key, List.of()).stream()
            .noneMatch(method -> method.qualifiers().contains(site.qualifier()));
  }

  private boolean isProjectType(final String type) {
    return owners.contains(type) || owners.contains(simpleName(type));
  }

  private static String simpleName(final String type) {
    return type.substring(type.lastIndexOf('.') + 1);
  }

  private static FlowSite marked(final FlowSite site) {
    final List<String> annotations = new ArrayList<>(site.annotations());
    annotations.add(MARK);
    return new FlowSite(
        site.kind(),
        site.name(),
        site.qualifier(),
        site.receiverType(),
        annotations,
        site.arguments(),
        site.span()
    );
  }

  /**
   * The assumption of a value at {@code span} of the block: the first marked call of the block that
   * takes it as an argument, within {@code within}.
   *
   * @param block the block of the value
   * @param within the statement or argument that the value flows through
   * @param span the value
   * @return the assumption, or empty when no unfollowed call takes the value
   */
  static List<String> through(final BasicBlock block, final Span within, final Span span) {
    final Optional<FlowSite> call =
        block.sites().stream()
            .filter(site -> site.annotations().contains(MARK))
            .filter(site -> TaintSpans.contains(within, site.span()))
            .filter(
                site -> site.arguments().stream().anyMatch(arg -> TaintSpans.contains(arg, span))
            )
            .findFirst();
    return call.map(site -> List.of(reason(site))).orElse(List.of());
  }

  private static String reason(final FlowSite site) {
    final String name = site.qualifier().isEmpty() ? site.name() : site.qualifier() + "." + site.name();
    return "the call " + name + " is not followed";
  }
}
