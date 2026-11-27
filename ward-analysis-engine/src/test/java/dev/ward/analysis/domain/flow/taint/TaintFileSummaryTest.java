package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What the taint summary of a file holds, and what it ignores. */
class TaintFileSummaryTest {
  private static final int CALLEE = 10;
  static final TaintSpec SPEC =
      new TaintSpec(
          List.of(entry("source", Optional.empty(), List.of())),
          List.of(entry("sink", Optional.empty(), List.of(0))),
          List.of(),
          List.of()
      );

  static TaintEntry entry(
      final String name,
      final Optional<String> qualifier,
      final List<Integer> arguments
  ) {
    return new TaintEntry(FlowSiteKind.CALL, name, Optional.empty(), qualifier, arguments);
  }

  static Span span(final int offset) {
    return new Span(1, 1, 1, 2, offset, offset + 1);
  }

  static FlowSite call(final String name, final String qualifier, final int offset) {
    return new FlowSite(
        FlowSiteKind.CALL,
        name,
        qualifier,
        Optional.empty(),
        List.of(),
        List.of(),
        span(offset)
    );
  }

  /** The graph of {@code A#get(0)}: it returns the result of {@code inner}, {@code shift} later. */
  static ControlFlowGraph getter(final String inner, final int shift) {
    final SyntaxNode keyword =
        new SyntaxNode("token", span(shift), Map.of("text", "return"), List.of());
    final SyntaxNode value = new SyntaxNode("expression", span(shift + 1), Map.of(), List.of());
    final SyntaxNode statement =
        new SyntaxNode("returnStatement", span(shift), Map.of(), List.of(keyword, value));
    final BasicBlock body =
        new BasicBlock(
            0,
            List.of(statement),
            List.of(new FlowEdge(1, FlowEdgeKind.NORMAL)),
            List.of(),
            List.of(call(inner, "", shift + 1))
        );
    return new ControlFlowGraph(
        "A#get(0)",
        span(shift),
        List.of(body, new BasicBlock(1, List.of(), List.of(), List.of(), List.of())),
        0,
        1
    );
  }

  /** The graph of {@code B#use(0)}, which calls {@code A.get()}, {@code shift} later. */
  static ControlFlowGraph caller(final int shift) {
    final BasicBlock body =
        new BasicBlock(
            0,
            List.of(),
            List.of(new FlowEdge(1, FlowEdgeKind.NORMAL)),
            List.of(),
            List.of(call("get", "A", shift))
        );
    return new ControlFlowGraph(
        "B#use(0)",
        span(shift),
        List.of(body, new BasicBlock(1, List.of(), List.of(), List.of(), List.of())),
        0,
        1
    );
  }

  @Test
  void aMethodOfAnotherFileThatReturnsASourceIsASourceInTheSummary() {
    final String summary =
        TaintFileSummary.of(List.of(caller(0)), List.of(getter("source", CALLEE)), SPEC);

    assertThat(summary).startsWith("source ").contains("name=get").contains(
        "qualifier=Optional[A]"
    );
  }

  @Test
  void aMethodThatReturnsNoSourceLeavesTheSummaryEmpty() {
    assertThat(TaintFileSummary.of(List.of(caller(0)), List.of(getter("other", CALLEE)), SPEC))
        .isEmpty();
  }

  @Test
  void aChangeOfLayoutOnlyDoesNotChangeTheSummary() {
    final String before = TaintFileSummary.of(
        List.of(caller(0)),
        List.of(getter("source", CALLEE)),
        SPEC
    );
    final String after = TaintFileSummary.of(
        List.of(caller(40)),
        List.of(getter("source", 90)),
        SPEC
    );

    assertThat(after).isEqualTo(before).isNotEmpty();
  }

  @Test
  void aCallResolvedToAnOwnerIsPartOfTheSummary() {
    final FlowSite typed =
        new FlowSite(
            FlowSiteKind.CALL,
            "get",
            "a",
            Optional.of("A"),
            List.of(),
            List.of(),
            span(0)
        );
    final ControlFlowGraph untyped = caller(0);
    final BasicBlock block =
        new BasicBlock(
            0,
            List.of(),
            untyped.blocks().get(0).successors(),
            List.of(),
            List.of(typed)
        );
    final ControlFlowGraph viaVariable =
        new ControlFlowGraph(
            untyped.body(),
            untyped.span(),
            List.of(block, untyped.blocks().get(1)),
            0,
            1
        );

    assertThat(TaintFileSummary.of(List.of(viaVariable), List.of(getter("other", CALLEE)), SPEC))
        .contains("call 0.0.0 A");
  }

  @Test
  void severalSpecificationsGiveTheirSummariesInOrder() {
    final TaintSpec other =
        new TaintSpec(
            List.of(entry("other", Optional.empty(), List.of())),
            List.of(),
            List.of(),
            List.of()
        );
    final List<ControlFlowGraph> related = List.of(getter("source", CALLEE));

    final String both = TaintFileSummary.of(List.of(caller(0)), related, List.of(SPEC, other));

    assertThat(both)
        .isEqualTo(TaintFileSummary.of(List.of(caller(0)), related, SPEC) + "\n--\n");
  }
}
