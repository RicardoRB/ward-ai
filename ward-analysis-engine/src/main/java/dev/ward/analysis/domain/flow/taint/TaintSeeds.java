package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Taints the writes that a source produces in one graph: the entry write of an annotated parameter,
 * and the last write before a call or member read in the statement that contains it. Sanitized
 * sources and sources outside any statement taint nothing.
 */
final class TaintSeeds {
  private static final String SOURCE = "source: ";

  private final ControlFlowGraph graph;
  private final TaintSpec spec;
  private final Map<AccessSite, Taint> tainted;

  TaintSeeds(
      final ControlFlowGraph graph,
      final TaintSpec spec,
      final Map<AccessSite,
      Taint> tainted
  ) {
    this.graph = graph;
    this.spec = spec;
    this.tainted = tainted;
  }

  /** Seeds every source of the graph: the parameters and the values that statements take in. */
  void seed() {
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowSite site : block.sites()) {
        seedSite(block, site);
      }
    }
  }

  private void seedSite(final BasicBlock block, final FlowSite site) {
    final Optional<TaintEntry> entry = TaintSpans.sourceOf(site, spec);
    if (entry.isEmpty() || isArgumentLiteral(site)) {
      return;
    }
    if (site.kind() == FlowSiteKind.PARAMETER) {
      seedParameter(block, site, entry.get());
    } else if (!TaintSpans.sanitized(block, site.span(), spec)) {
      seedValue(block, site, entry.get());
    }
  }

  /** A literal that is a call argument is a source only where it is a guarded argument. */
  private static boolean isArgumentLiteral(final FlowSite site) {
    return site.kind() == FlowSiteKind.LITERAL && "argument".equals(site.qualifier());
  }

  /** The incoming value of an annotated parameter is the source, so the entry write is tainted. */
  private void seedParameter(final BasicBlock block, final FlowSite site, final TaintEntry entry) {
    IntStream.range(0, block.accesses().size())
        .filter(index -> isParameterNamed(block.accesses().get(index), site.name()))
        .forEach(index -> taintParameter(block, index, site, entry));
  }

  private static boolean isParameterNamed(final VariableAccess access, final String name) {
    return access.kind() == AccessKind.PARAMETER && access.name().equals(name);
  }

  private void taintParameter(
      final BasicBlock block,
      final int index,
      final FlowSite site,
      final TaintEntry entry
  ) {
    final List<FlowStep> path = List.of(new FlowStep(site.span(), SOURCE + entry.name()));
    tainted.put(
        new AccessSite(block.id(), index, block.accesses().get(index)),
        new Taint(entry.name(), path, entry.assumptions())
    );
  }

  /**
   * A call or member read taints the last write before it in the same statement. A statement with
   * a conditional or logical operator is split over several blocks, so the write can be in another
   * block than the read: the one whose statement holds the read.
   */
  private void seedValue(final BasicBlock block, final FlowSite site, final TaintEntry entry) {
    final Optional<AccessSite> write =
        graph.blocks().stream()
            .sorted(Comparator.comparingInt((BasicBlock other) -> other.id() == block.id() ? 0 : 1))
            .map(other -> writeInStatementOf(other, site))
            .flatMap(Optional::stream)
            .findFirst();
    final List<String> through =
        TaintSpans.nodeContaining(block, site.span())
            .map(node -> UnfollowedCalls.through(block, node.span(), site.span()))
            .orElse(List.of());
    final Taint taint =
        new Taint(
            site.name(),
            List.of(new FlowStep(site.span(), SOURCE + site.name())),
            entry.assumptions()
        ).assuming(through);
    write.ifPresent(definition -> tainted.merge(definition, taint, Taint::better));
  }

  private Optional<AccessSite> writeInStatementOf(final BasicBlock block, final FlowSite site) {
    final boolean here = block.sites().contains(site);
    return TaintSpans.nodeContaining(block, site.span())
        .filter(node -> here || SplitValues.covers(block, site.span()))
        .flatMap(node -> lastWriteBefore(block, node.span(), site.span()));
  }

  private Optional<AccessSite> lastWriteBefore(
      final BasicBlock block,
      final Span statement,
      final Span before
  ) {
    return IntStream.range(0, block.accesses().size())
        .filter(index -> isWriteBefore(block.accesses().get(index), statement, before))
        .boxed()
        .reduce((earlier, later) -> later)
        .map(index -> new AccessSite(block.id(), index, block.accesses().get(index)));
  }

  private static boolean isWriteBefore(
      final VariableAccess access,
      final Span statement,
      final Span before
  ) {
    return isWrite(access)
        && TaintSpans.contains(statement, access.span())
        && access.span().startOffset() < before.startOffset();
  }

  private static boolean isWrite(final VariableAccess access) {
    return access.kind() == AccessKind.WRITE;
  }
}
