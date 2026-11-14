package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A container that a tainted value is stored into is tainted; a clean store leaves it clean. */
class TaintStoresTest {
  private static final Span DECLARATION = new Span(1, 1, 1, 11, 0, 10);
  private static final Span WRITE = new Span(1, 1, 1, 2, 0, 1);
  private static final Span STORE_READ = new Span(2, 1, 2, 2, 11, 12);
  private static final Span VALUE = new Span(2, 8, 2, 16, 18, 26);
  private static final Span SINK_READ = new Span(3, 6, 3, 7, 36, 37);

  private static final TaintSpec SPEC =
      new TaintSpec(
          List.of(entry("source", List.of())),
          List.of(entry("sink", List.of(0))),
          List.of(),
          List.of(entry("add", List.of(0))));

  private static TaintEntry entry(final String name, final List<Integer> arguments) {
    return new TaintEntry(FlowSiteKind.CALL, name, Optional.empty(), Optional.empty(), arguments);
  }

  private static FlowSite site(
      final FlowSiteKind kind,
      final String name,
      final String qualifier,
      final Span argument,
      final Span span) {
    return new FlowSite(
        kind,
        name,
        qualifier,
        Optional.empty(),
        List.of(),
        argument == null ? List.of() : List.of(argument),
        span);
  }

  private static SyntaxNode statement(final Span span) {
    return new SyntaxNode("statement", span, Map.of(), List.of());
  }

  /** {@code c = ...; <store>; sink(c)}, where the store puts {@code value} into {@code c}. */
  private static ControlFlowGraph graph(final FlowSite store, final String value) {
    final Span sourceCall = new Span(2, 8, 2, 16, 18, 26);
    final FlowSite stored =
        value.isEmpty() ? null : site(FlowSiteKind.CALL, value, "", null, sourceCall);
    final List<FlowSite> sites = new ArrayList<>();
    if (stored != null) {
      sites.add(stored);
    }
    sites.add(store);
    sites.add(site(FlowSiteKind.CALL, "sink", "", SINK_READ, new Span(3, 1, 3, 9, 31, 39)));
    final BasicBlock body =
        new BasicBlock(
            0,
            List.of(
                statement(DECLARATION),
                statement(new Span(2, 1, 2, 30, 11, 30)),
                statement(new Span(3, 1, 3, 10, 31, 40))),
            List.of(new FlowEdge(1, FlowEdgeKind.NORMAL)),
            List.of(
                new VariableAccess("c", AccessKind.WRITE, WRITE, DECLARATION),
                new VariableAccess("c", AccessKind.READ, STORE_READ, DECLARATION),
                new VariableAccess("c", AccessKind.READ, SINK_READ, DECLARATION)),
            sites);
    return new ControlFlowGraph(
        "A#m(0)",
        new Span(1, 1, 3, 10, 0, 40),
        List.of(body, new BasicBlock(1, List.of(), List.of(), List.of(), List.of())),
        0,
        1);
  }

  private static FlowSite memberWrite() {
    return site(FlowSiteKind.MEMBER_WRITE, "f", "c", VALUE, new Span(2, 1, 2, 17, 11, 27));
  }

  private static FlowSite addCall() {
    return site(FlowSiteKind.CALL, "add", "c", VALUE, new Span(2, 3, 2, 17, 13, 27));
  }

  @Test
  void aTaintedValueStoredIntoAMemberTaintsTheVariable() {
    final List<TaintFinding> found = TaintAnalysis.run(graph(memberWrite(), "source"), SPEC);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).path().get(1).message()).isEqualTo("stored into c");
  }

  @Test
  void aTaintedValueGivenToAPropagatorCallTaintsItsReceiver() {
    assertThat(TaintAnalysis.run(graph(addCall(), "source"), SPEC)).hasSize(1);
  }

  @Test
  void aCleanValueLeavesTheContainerClean() {
    assertThat(TaintAnalysis.run(graph(memberWrite(), ""), SPEC)).isEmpty();
    assertThat(TaintAnalysis.run(graph(addCall(), ""), SPEC)).isEmpty();
  }

  @Test
  void aTargetThatIsNotAPlainVariableIsNotAContainer() {
    final FlowSite onThis =
        site(FlowSiteKind.MEMBER_WRITE, "f", "this", VALUE, new Span(2, 1, 2, 17, 11, 27));
    final FlowSite chained =
        site(FlowSiteKind.CALL, "add", "c.items", VALUE, new Span(2, 3, 2, 17, 13, 27));

    assertThat(TaintAnalysis.run(graph(onThis, "source"), SPEC)).isEmpty();
    assertThat(TaintAnalysis.run(graph(chained, "source"), SPEC)).isEmpty();
  }

  @Test
  void aCallThatNoPropagatorNamesStoresNothing() {
    final FlowSite other =
        site(FlowSiteKind.CALL, "remove", "c", VALUE, new Span(2, 3, 2, 17, 13, 27));

    assertThat(TaintAnalysis.run(graph(other, "source"), SPEC)).isEmpty();
  }
}
