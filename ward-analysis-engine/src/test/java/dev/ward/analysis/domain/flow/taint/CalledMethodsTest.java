package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalledMethodsTest {
  private static final Span SPAN = new Span(1, 1, 1, 10, 0, 9);

  private static LocalMethod method(final String name, final String... calls) {
    final List<FlowSite> sites =
        java.util.Arrays.stream(calls)
            .map(
                call ->
                    new FlowSite(
                        FlowSiteKind.CALL,
                        call,
                        "",
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        SPAN
                    )
            )
            .toList();
    final BasicBlock block = new BasicBlock(0, List.of(), List.of(), List.of(), sites);
    final ControlFlowGraph graph = new ControlFlowGraph(
        "p:Owner#" + name + "(0)",
        SPAN,
        List.of(block, new BasicBlock(1, List.of(), List.of(), List.of(), List.of())),
        0,
        1
    );
    return new LocalMethod(name, "p.Owner", 0, graph, true);
  }

  @Test
  void aMethodCalledByAReachedMethodIsReachedToo() {
    final LocalMethod first = method("first", "second");
    final LocalMethod second = method("second", "third");
    final LocalMethod third = method("third");
    final LocalMethod unused = method("unused");
    final Map<String, LocalMethod> external =
        Map.of(first.key(), first, second.key(), second, third.key(), third, unused.key(), unused);
    final ControlFlowGraph caller = method("caller", "first").graph();
    assertThat(CalledMethods.reachable(List.of(caller), external).keySet())
        .containsExactlyInAnyOrder(first.key(), second.key(), third.key());
  }

  @Test
  void aCycleBetweenMethodsEnds() {
    final LocalMethod ping = method("ping", "pong");
    final LocalMethod pong = method("pong", "ping");
    final ControlFlowGraph caller = method("caller", "ping").graph();
    assertThat(
        CalledMethods.reachable(
            List.of(caller),
            Map.of(ping.key(), ping, pong.key(), pong)
        ).keySet()
    ).containsExactlyInAnyOrder(ping.key(), pong.key());
  }
}
