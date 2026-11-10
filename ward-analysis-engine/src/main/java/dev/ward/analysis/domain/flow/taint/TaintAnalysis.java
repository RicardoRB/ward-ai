package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Finds, within one function's control-flow graph, the values from a source that reach an argument
 * guarded by a sink. A finding's path lists the source, each write that propagated the value, and
 * the sink, in that order. Blocks and sites are visited in order, so the same source text and
 * specification always give the same findings and paths.
 */
public final class TaintAnalysis {

  private static final String SINK = "sink: ";
  private static final String ARGUMENT = " argument ";
  private static final String SOURCE = "source: ";

  private TaintAnalysis() {}

  /**
   * Runs the analysis on one graph.
   *
   * @param graph the control-flow graph of a function
   * @param spec the taint specification
   * @return one finding per sink argument that receives a tainted value, in block order
   */
  public static List<TaintFinding> run(final ControlFlowGraph graph, final TaintSpec spec) {
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);
    final Map<AccessSite, Taint> origins = TaintOrigins.of(graph, reaching, spec);
    final List<TaintFinding> found = new ArrayList<>();
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowSite site : block.sites()) {
        found.addAll(sinkFindings(block, site, spec, reaching, origins));
      }
    }
    return found;
  }

  private static List<TaintFinding> sinkFindings(
      final BasicBlock block,
      final FlowSite site,
      final TaintSpec spec,
      final ReachingDefinitions reaching,
      final Map<AccessSite, Taint> origins) {
    final List<TaintFinding> found = new ArrayList<>();
    final Optional<TaintEntry> sink =
        isSinkKind(site.kind()) ? TaintSpans.sinkOf(site, spec) : Optional.empty();
    if (sink.isEmpty()) {
      return found;
    }
    for (final int index : guardedArguments(site, sink.get())) {
      if (index < site.arguments().size()) {
        final Span argument = site.arguments().get(index);
        taintOf(block, argument, reaching, origins, spec)
            .map(taint -> finding(site, index, taint))
            .ifPresent(found::add);
      }
    }
    return found;
  }

  /** The taint that reaches one guarded argument: a source written in it, or a tainted read. */
  private static Optional<Taint> taintOf(
      final BasicBlock block,
      final Span argument,
      final ReachingDefinitions reaching,
      final Map<AccessSite, Taint> origins,
      final TaintSpec spec) {
    return directSource(block, argument, spec)
        .or(() -> fromReads(block, argument, reaching, origins, spec));
  }

  /**
   * A call, a property write whose argument 0 is the assigned value, or a string build can be a
   * sink.
   */
  private static boolean isSinkKind(final FlowSiteKind kind) {
    return kind == FlowSiteKind.CALL
        || kind == FlowSiteKind.MEMBER_WRITE
        || kind == FlowSiteKind.STRING_BUILD;
  }

  /**
   * A string entry guards every value part of the string; other sinks guard their listed positions.
   */
  private static List<Integer> guardedArguments(final FlowSite site, final TaintEntry sink) {
    if (site.kind() != FlowSiteKind.STRING_BUILD) {
      return sink.arguments();
    }
    return IntStream.range(0, site.arguments().size()).boxed().toList();
  }

  private static TaintFinding finding(final FlowSite site, final int index, final Taint taint) {
    return new TaintFinding(site, taint.source(), withSink(taint, site, index));
  }

  /** A source call written directly in the guarded argument, such as {@code sink(source())}. */
  private static Optional<Taint> directSource(
      final BasicBlock block, final Span argument, final TaintSpec spec) {
    return block.sites().stream()
        .filter(site -> TaintSpans.contains(argument, site.span()))
        .filter(site -> site.kind() != FlowSiteKind.PARAMETER)
        .filter(site -> TaintSpans.sourceOf(site, spec).isPresent())
        .filter(site -> !TaintSpans.sanitized(block, site.span(), spec))
        .findFirst()
        .map(
            site ->
                new Taint(site.name(), List.of(new FlowStep(site.span(), SOURCE + site.name()))));
  }

  /**
   * A read in the guarded argument that sees a tainted write. A read inside a sanitizer's argument
   * is clean, as it is for a write.
   */
  private static Optional<Taint> fromReads(
      final BasicBlock block,
      final Span argument,
      final ReachingDefinitions reaching,
      final Map<AccessSite, Taint> origins,
      final TaintSpec spec) {
    for (final AccessSite use : reaching.uses()) {
      if (use.block() == block.id()
          && isRead(use.access())
          && TaintSpans.contains(argument, use.access().span())
          && !TaintSpans.sanitized(block, use.access().span(), spec)) {
        final Optional<Taint> taint =
            reaching.reaching(use).stream().map(origins::get).filter(t -> t != null).findFirst();
        if (taint.isPresent()) {
          return taint;
        }
      }
    }
    return Optional.empty();
  }

  private static boolean isRead(final VariableAccess access) {
    return access.kind() == AccessKind.READ;
  }

  private static String sinkStep(final FlowSite site, final int index) {
    return SINK + site.name() + ARGUMENT + index;
  }

  private static List<FlowStep> withSink(final Taint taint, final FlowSite site, final int index) {
    final List<FlowStep> path = new ArrayList<>(taint.path());
    path.add(new FlowStep(site.span(), sinkStep(site, index)));
    return path;
  }
}
