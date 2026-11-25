package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The graphs and sites of the {@link GuardedArgumentTest} scenarios. */
final class GuardedGraphs {
  static final String RESPONSE = "res";
  static final Span ARGUMENT = span(100, 130);
  static final Span INSIDE = span(105, 106);
  static final FlowSite STRINGIFY = call("stringify", "JSON", span(100, 114));
  static final FlowSite END = call("end", RESPONSE, span(90, 99));

  private static final int BODY_END = 300;
  private static final int JOIN = 3;
  private static final int LAST = 4;
  private static final int TYPED_AT = 10;

  /** A graph and the block that holds the sink. */
  record Fixture(ControlFlowGraph graph, BasicBlock sink) {}

  private GuardedGraphs() {}

  /** One block with the sites, then the exit. */
  static Fixture straight(final List<FlowSite> sites) {
    final List<BasicBlock> blocks = List.of(block(0, sites, 1), block(1, List.of()));
    return new Fixture(graph(blocks, 1), blocks.get(0));
  }

  /** The type is set in the entry, which both the sink block and a branch follow. */
  static Fixture dominating() {
    final List<BasicBlock> blocks =
        List.of(
            block(0, List.of(typed(RESPONSE, TYPED_AT)), 1, 2),
            block(1, List.of(STRINGIFY, END), JOIN),
            block(2, List.of(), JOIN),
            block(JOIN, List.of())
        );
    return new Fixture(graph(blocks, JOIN), blocks.get(1));
  }

  /** The type is set on one of two branches that join before the sink. */
  static Fixture oneBranch() {
    final List<BasicBlock> blocks =
        List.of(
            block(0, List.of(), 1, 2),
            block(1, List.of(typed(RESPONSE, TYPED_AT)), JOIN),
            block(2, List.of(), JOIN),
            block(JOIN, List.of(STRINGIFY, END), LAST),
            block(LAST, List.of())
        );
    return new Fixture(graph(blocks, LAST), blocks.get(JOIN));
  }

  /** A call that sets a JSON content type on the response. */
  static FlowSite typed(final String qualifier, final int offset) {
    return typed("json", qualifier, offset);
  }

  /** A call that sets a content type of the kind on the response. */
  static FlowSite typed(final String kind, final String qualifier, final int offset) {
    return new FlowSite(
        FlowSiteKind.CONTENT_TYPE,
        kind,
        qualifier,
        Optional.empty(),
        List.of(),
        List.of(),
        span(offset, offset + 1)
    );
  }

  static FlowSite call(final String name, final String qualifier, final Span site) {
    return new FlowSite(
        FlowSiteKind.CALL,
        name,
        qualifier,
        Optional.empty(),
        List.of(),
        List.of(ARGUMENT),
        site
    );
  }

  static Span span(final int start, final int end) {
    return new Span(1, start + 1, 1, end + 1, start, end);
  }

  private static ControlFlowGraph graph(final List<BasicBlock> blocks, final int exit) {
    return new ControlFlowGraph("f", span(0, BODY_END), blocks, 0, exit);
  }

  private static BasicBlock block(final int id, final List<FlowSite> sites, final int... next) {
    final List<FlowEdge> edges =
        Arrays.stream(next).mapToObj(to -> new FlowEdge(to, FlowEdgeKind.NORMAL)).toList();
    return new BasicBlock(id, List.of(), edges, List.of(), sites);
  }
}
