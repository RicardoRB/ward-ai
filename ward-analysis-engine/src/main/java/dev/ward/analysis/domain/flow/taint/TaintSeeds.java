package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
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
      final ControlFlowGraph graph, final TaintSpec spec, final Map<AccessSite, Taint> tainted) {
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
    if (entry.isEmpty()) {
      return;
    }
    if (site.kind() == FlowSiteKind.PARAMETER) {
      seedParameter(block, site, entry.get().name());
    } else if (!TaintSpans.sanitized(block, site.span(), spec)) {
      seedValue(block, site);
    }
  }

  /** The incoming value of an annotated parameter is the source, so the entry write is tainted. */
  private void seedParameter(final BasicBlock block, final FlowSite site, final String annotation) {
    IntStream.range(0, block.accesses().size())
        .filter(index -> isParameterNamed(block.accesses().get(index), site.name()))
        .forEach(index -> taintParameter(block, index, site, annotation));
  }

  private static boolean isParameterNamed(final VariableAccess access, final String name) {
    return access.kind() == AccessKind.PARAMETER && access.name().equals(name);
  }

  private void taintParameter(
      final BasicBlock block, final int index, final FlowSite site, final String annotation) {
    final List<FlowStep> path = List.of(new FlowStep(site.span(), SOURCE + annotation));
    tainted.put(
        new AccessSite(block.id(), index, block.accesses().get(index)),
        new Taint(annotation, path));
  }

  /** A call or member read taints the last write before it in the same statement. */
  private void seedValue(final BasicBlock block, final FlowSite site) {
    final Optional<SyntaxNode> node = TaintSpans.nodeContaining(block, site.span());
    if (node.isEmpty()) {
      return;
    }
    final Optional<AccessSite> write = lastWriteBefore(block, node.get().span(), site.span());
    write.ifPresent(
        definition ->
            tainted.putIfAbsent(
                definition,
                new Taint(site.name(), List.of(new FlowStep(site.span(), SOURCE + site.name())))));
  }

  private Optional<AccessSite> lastWriteBefore(
      final BasicBlock block, final Span statement, final Span before) {
    return IntStream.range(0, block.accesses().size())
        .filter(index -> isWriteBefore(block.accesses().get(index), statement, before))
        .boxed()
        .reduce((earlier, later) -> later)
        .map(index -> new AccessSite(block.id(), index, block.accesses().get(index)));
  }

  private static boolean isWriteBefore(
      final VariableAccess access, final Span statement, final Span before) {
    return isWrite(access)
        && TaintSpans.contains(statement, access.span())
        && access.span().startOffset() < before.startOffset();
  }

  private static boolean isWrite(final VariableAccess access) {
    return access.kind() == AccessKind.WRITE;
  }
}
