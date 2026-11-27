package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * A caller that saw the list of {@code readFixes(key)} not empty knows that the key starts the name
 * of an entry, when the function says so: hand-built graphs of the function and its caller.
 */
class ListingLookupsTest {
  private static final String KEY = "key";
  private static final String DATA = "data";
  private static final String LIST = "fixes";
  private static final int WITH_CLEAN = 4;
  private static final int WITHOUT_CLEAN = 3;
  private static final TaintSpec SPEC = new TaintSpec(List.of(), List.of(), List.of(), List.of());
  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  private static Span span(final int at) {
    return new Span(1, at, 1, at + 1, at, at + 1);
  }

  private static ControlFlowGraph reader(final String list, final boolean reassigned) {
    final List<VariableAccess> accesses = new ArrayList<>();
    accesses.add(new VariableAccess(KEY, AccessKind.PARAMETER, span(1), span(1)));
    if (reassigned) {
      accesses.add(new VariableAccess(KEY, AccessKind.WRITE, span(2), span(1)));
    }
    final FlowSite mark =
        new FlowSite(
            FlowSiteKind.CALL,
            ListingMarks.PREFIX_LISTING,
            KEY,
            Optional.empty(),
            List.of(list),
            List.of(),
            SPAN
        );
    return GraphParts.graph("readFixes", List.of(), accesses, List.of(mark));
  }

  private static ControlFlowGraph caller(final String checkedList) {
    final Span keyRead = span(3);
    final List<VariableAccess> accesses =
        List.of(
            new VariableAccess(KEY, AccessKind.WRITE, span(4), span(4)),
            new VariableAccess(KEY, AccessKind.READ, keyRead, span(4)),
            new VariableAccess(DATA, AccessKind.WRITE, span(5), span(5))
        );
    final FlowSite call = GraphParts.call("readFixes", span(6), List.of(keyRead));
    final FlowSite check =
        new FlowSite(
            FlowSiteKind.CALL,
            ListingMarks.LENGTH_CHECKED,
            KEY,
            Optional.empty(),
            List.of("readFixes", DATA, checkedList),
            List.of(),
            span(7)
        );
    return GraphParts.graph("serve", List.of(), accesses, List.of(call, check));
  }

  private static ControlFlowGraph checked(
      final ControlFlowGraph reader,
      final ControlFlowGraph caller
  ) {
    final LocalMethod method = new LocalMethod("readFixes", "", 1, reader, false);
    final UnaryOperator<ControlFlowGraph> lookups =
        MemberLookups.over(
            Map.of(method.key(), method),
            List.of(caller),
            List.of(reader, caller),
            SPEC
        );
    return lookups.apply(caller);
  }

  @Test
  void aListingThatTheFunctionVouchesForWritesTheKeyCleanAtTheCheck() {
    final ControlFlowGraph graph = checked(reader(LIST, false), caller(LIST));

    final VariableAccess first = graph.blocks().get(0).accesses().get(0);
    assertThat(first.name()).isEqualTo(KEY);
    assertThat(first.kind()).isEqualTo(AccessKind.WRITE);
    assertThat(graph.blocks().get(0).accesses()).hasSize(WITH_CLEAN);
  }

  @Test
  void aCheckOfAnotherListLeavesTheCallerAsItIs() {
    assertThat(checked(reader(LIST, false), caller("other")).blocks().get(0).accesses())
        .hasSize(WITHOUT_CLEAN);
  }

  @Test
  void aFunctionThatReassignsItsKeyDoesNotVouch() {
    assertThat(checked(reader(LIST, true), caller(LIST)).blocks().get(0).accesses())
        .hasSize(WITHOUT_CLEAN);
  }

  @Test
  void aFunctionWithoutTheMarkDoesNotVouch() {
    final ControlFlowGraph bare = GraphParts.graph(
        "readFixes",
        List.of(),
        List.of(new VariableAccess(KEY, AccessKind.PARAMETER, span(1), span(1))),
        List.of()
    );

    assertThat(checked(bare, caller(LIST)).blocks().get(0).accesses()).hasSize(WITHOUT_CLEAN);
  }

  @Test
  void aListingCheckIsMarkedButNeedsNoDependentFiles() {
    final ControlFlowGraph caller = caller(LIST);

    assertThat(ListingMarks.checked(caller)).isTrue();
    assertThat(MemberLookups.marked(caller)).isTrue();
    assertThat(MemberVouching.mayVouch(List.of(caller))).isFalse();
    assertThat(ListingMarks.checked(reader(LIST, false))).isFalse();
  }
}
