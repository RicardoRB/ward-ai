package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Finds where the values of one source go, not whether they reach a sink: the places that the
 * source taints, and every read of a tainted variable. A rule that must know all the uses of a
 * value reads this, and classifies each use itself.
 *
 * <p>A method that returns a value of the source is a source for its callers, as in {@link
 * TaintFile}, so the values that cross a call are followed too: the call is an origin of its own.
 */
public final class TaintedUses {
  private static final String NEUTRAL = "ward:other-origin";

  private TaintedUses() {}

  /**
   * One place that produces a value of the source.
   *
   * @param graph the index of the graph of the origin among the graphs given
   * @param site the call that produces the value
   * @param derived whether the call is to a method that returns a value of the source, not a call
   *     that the specification names
   */
  public record Origin(int graph, FlowSite site, boolean derived) {}

  /**
   * One read of a variable that holds a value of the source.
   *
   * @param graph the index of the graph of the read among the graphs given
   * @param span where the variable is read
   * @param origin where the value that the variable holds came from
   */
  public record Read(int graph, Span span, Span origin) {}

  /**
   * What the source reaches.
   *
   * @param origins every place that produces a value of the source, in graph and source order
   * @param reads every read of a variable that holds such a value, in graph and source order
   */
  public record Found(List<Origin> origins, List<Read> reads) {
    /** Copies the lists. */
    public Found {
      origins = List.copyOf(origins);
      reads = List.copyOf(reads);
    }
  }

  /**
   * Follows the source through the graphs of a file and the files it depends on.
   *
   * @param graphs the control-flow graphs of the file, in source order
   * @param related the graphs of the files it depends on
   * @param spec the specification: its sources are the source; its sinks are not read
   * @param extra more sources, followed whatever the choice of {@code only}: the calls to a method
   *     that a caller knows returns the value, whatever the number of its arguments
   * @param only the call that is the only origin that the specification names, as given by {@link
   *     FlowSite#span()}; the other calls that match a source are not followed, so one result
   *     describes one value. Empty to follow all of them.
   * @return the origins and reads of the file
   */
  public static Found of(
      final List<ControlFlowGraph> graphs,
      final List<ControlFlowGraph> related,
      final TaintSpec spec,
      final List<TaintEntry> extra,
      final Optional<Span> only
  ) {
    final List<ControlFlowGraph> own = only.map(span -> neutral(graphs, spec, span)).orElse(graphs);
    final List<TaintEntry> sources = new ArrayList<>(spec.sources());
    sources.addAll(extra);
    final TaintSpec widened =
        new TaintSpec(sources, spec.sinks(), spec.sanitizers(), spec.propagators());
    final TaintFile.Prepared prepared =
        TaintFile.prepare(TaintContext.of(own, related), own, widened);
    final List<Origin> origins = new ArrayList<>();
    final List<Read> reads = new ArrayList<>();
    for (int index = 0; index < prepared.own().size(); index++) {
      final ControlFlowGraph graph = prepared.own().get(index);
      if (TaintSpans.hasSource(graph, prepared.effective())) {
        origins.addAll(originsOf(index, graph, spec, prepared.effective()));
        reads.addAll(readsOf(index, graph, prepared.effective()));
      }
    }
    return new Found(origins, reads);
  }

  private static List<Origin> originsOf(
      final int index,
      final ControlFlowGraph graph,
      final TaintSpec spec,
      final TaintSpec effective
  ) {
    return graph.blocks().stream()
        .flatMap(block -> block.sites().stream())
        .filter(site -> TaintSpans.sourceOf(site, effective).isPresent())
        .map(site -> origin(index, site, spec))
        .toList();
  }

  private static Origin origin(final int index, final FlowSite site, final TaintSpec spec) {
    return new Origin(index, site, spec.sources().stream().noneMatch(entry -> entry.matches(site)));
  }

  private static List<Read> readsOf(
      final int index,
      final ControlFlowGraph graph,
      final TaintSpec effective
  ) {
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);
    final Map<AccessSite, Taint> tainted = TaintOrigins.of(graph, reaching, effective);
    return reaching.uses().stream()
        .filter(use -> use.access().kind() == AccessKind.READ)
        .flatMap(
            use -> reaching.reaching(use).stream()
                .map(tainted::get)
                .filter(Objects::nonNull)
                .map(taint -> taint.path().get(0).span())
                .distinct()
                .map(origin -> read(index, use, origin))
        )
        .toList();
  }

  private static Read read(final int index, final AccessSite use, final Span origin) {
    return new Read(index, use.access().span(), origin);
  }

  /** The graphs with every call that matches a source of the spec renamed, but the one at {@code span}. */
  private static List<ControlFlowGraph> neutral(
      final List<ControlFlowGraph> graphs,
      final TaintSpec spec,
      final Span span
  ) {
    return graphs.stream().map(graph -> neutral(graph, spec, span)).collect(Collectors.toList());
  }

  private static ControlFlowGraph neutral(
      final ControlFlowGraph graph,
      final TaintSpec spec,
      final Span span
  ) {
    final List<BasicBlock> blocks =
        graph.blocks().stream().map(block -> neutral(block, spec, span)).toList();
    return blocks.equals(graph.blocks())
        ? graph
        : new ControlFlowGraph(graph.body(), graph.span(), blocks, graph.entry(), graph.exit());
  }

  private static BasicBlock neutral(final BasicBlock block, final TaintSpec spec, final Span span) {
    final List<FlowSite> sites =
        block.sites().stream().map(site -> neutral(site, spec, span)).toList();
    return sites.equals(block.sites())
        ? block
        : new BasicBlock(block.id(), block.nodes(), block.successors(), block.accesses(), sites);
  }

  private static FlowSite neutral(final FlowSite site, final TaintSpec spec, final Span span) {
    final boolean other =
        !site.span().equals(span) && spec.sources().stream().anyMatch(entry -> entry.matches(site));
    return other ? renamed(site) : site;
  }

  private static FlowSite renamed(final FlowSite site) {
    return new FlowSite(
        site.kind(),
        NEUTRAL,
        site.qualifier(),
        Optional.empty(),
        site.annotations(),
        site.arguments(),
        site.span()
    );
  }
}
