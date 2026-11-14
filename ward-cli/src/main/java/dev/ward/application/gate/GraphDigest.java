package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes the control-flow graphs of a file as text that does not change when only the layout of the
 * source changes. Everything the analyses read from a graph is in the text, but a position is
 * replaced by its rank among the offsets of its graph, since the analyses only compare positions: a
 * comment or a blank line that moves code leaves the ranks, and so the text, as it was. The text of
 * a comment node is left out, and the node itself stays.
 */
final class GraphDigest {

  private static final String COMMENT = "comment";
  private static final String SEPARATOR = ";";

  private final List<String> lines = new ArrayList<>();
  private GraphRanks ranks;

  private GraphDigest() {}

  /** Returns the layout-free text of the graphs, in the order given. */
  static String of(final List<ControlFlowGraph> graphs) {
    final GraphDigest digest = new GraphDigest();
    graphs.forEach(digest::graph);
    return String.join("\n", digest.lines);
  }

  private void graph(final ControlFlowGraph graph) {
    ranks = new GraphRanks(graph);
    line(
        "graph",
        graph.body(),
        String.valueOf(graph.entry()),
        String.valueOf(graph.exit()),
        ranks.of(graph.span()));
    graph.blocks().forEach(this::block);
  }

  private void line(final String... parts) {
    lines.add(String.join(SEPARATOR, parts));
  }

  private void block(final BasicBlock block) {
    final String edges =
        block.successors().stream()
            .map(edge -> edge.target() + edge.kind().name())
            .collect(Collectors.joining(","));
    line("b" + block.id(), edges);
    block.nodes().forEach(this::node);
    block.accesses().forEach(this::access);
    block.sites().forEach(this::site);
  }

  private void node(final SyntaxNode node) {
    final String fields =
        COMMENT.equals(node.kind())
            ? ""
            : node.fields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    line("n", node.kind(), ranks.of(node.span()), fields, "{");
    node.children().forEach(this::node);
    line("}");
  }

  private void access(final VariableAccess access) {
    line(
        "a",
        access.name(),
        access.kind().name(),
        ranks.of(access.span()),
        ranks.of(access.declaration()));
  }

  private void site(final FlowSite site) {
    final String arguments =
        site.arguments().stream().map(ranks::of).collect(Collectors.joining(","));
    line(
        Stream.of(
                "s",
                site.kind().name(),
                site.name(),
                site.qualifier(),
                site.receiverType().orElse(""),
                String.join(",", site.annotations()),
                ranks.of(site.span()),
                arguments)
            .toArray(String[]::new));
  }
}
