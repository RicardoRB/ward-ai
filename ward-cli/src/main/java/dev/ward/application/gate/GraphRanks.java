package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Ranks the offsets of one control-flow graph: the smallest offset has rank 0, and equal offsets
 * share a rank. Two graphs whose offsets are ordered alike have the same ranks, whatever the gaps
 * between the offsets.
 */
final class GraphRanks {

  private final Map<Integer, Integer> ranks = new HashMap<>();

  GraphRanks(final ControlFlowGraph graph) {
    final List<Integer> sorted =
        spans(graph).flatMap(GraphRanks::offsets).distinct().sorted().toList();
    IntStream.range(0, sorted.size()).forEach(index -> ranks.put(sorted.get(index), index));
  }

  /** The text of a span: the ranks of its start and its end. */
  String of(final Span span) {
    return ranks.get(span.startOffset()) + "-" + ranks.get(span.endOffset());
  }

  private static Stream<Integer> offsets(final Span span) {
    return Stream.of(span.startOffset(), span.endOffset());
  }

  private static Stream<Span> spans(final ControlFlowGraph graph) {
    return Stream.concat(
        Stream.of(graph.span()), graph.blocks().stream().flatMap(GraphRanks::spans));
  }

  private static Stream<Span> spans(final BasicBlock block) {
    return Stream.of(
            block.nodes().stream().flatMap(GraphRanks::spans),
            block.accesses().stream().flatMap(GraphRanks::spans),
            block.sites().stream().flatMap(GraphRanks::spans))
        .flatMap(stream -> stream);
  }

  private static Stream<Span> spans(final SyntaxNode node) {
    return Stream.concat(
        Stream.of(node.span()), node.children().stream().flatMap(GraphRanks::spans));
  }

  private static Stream<Span> spans(final VariableAccess access) {
    return Stream.of(access.span(), access.declaration());
  }

  private static Stream<Span> spans(final FlowSite site) {
    return Stream.concat(Stream.of(site.span()), site.arguments().stream());
  }
}
