package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuardedCallsTest {
  @Test
  void aCheckThatThrowsOtherwiseChecksItsParameter() {
    final LocalMethod method = GuardedCallGraphs.method(
        GuardedCallGraphs.edge(GuardedCallGraphs.EXIT, FlowEdgeKind.EXCEPTION),
        List.of()
    );

    assertThat(GuardedCalls.checkedParameters(method)).containsExactly(0);
  }

  @Test
  void aCheckThatCanReturnWithoutPassingItDoesNotCheck() {
    final LocalMethod method = GuardedCallGraphs.method(
        GuardedCallGraphs.edge(GuardedCallGraphs.EXIT, FlowEdgeKind.NORMAL),
        List.of()
    );

    assertThat(GuardedCalls.checkedParameters(method)).isEmpty();
  }

  @Test
  void aParameterThatIsAssignedAgainIsNotChecked() {
    final VariableAccess other =
        new VariableAccess(
            "p",
            AccessKind.WRITE,
            GuardedCallGraphs.ARGUMENT,
            GuardedCallGraphs.DECLARATION
        );
    final LocalMethod method = GuardedCallGraphs.method(
        GuardedCallGraphs.edge(GuardedCallGraphs.EXIT, FlowEdgeKind.EXCEPTION),
        List.of(other)
    );

    assertThat(GuardedCalls.checkedParameters(method)).isEmpty();
  }

  @Test
  void aMethodWithoutAMarkChecksNothing() {
    final BasicBlock entry = GuardedCallGraphs.block(
        0,
        List.of(GuardedCallGraphs.edge(GuardedCallGraphs.EXIT, FlowEdgeKind.NORMAL)),
        List.of(),
        List.of()
    );

    assertThat(
        GuardedCalls.checkedParameters(
            GuardedCallGraphs.local(
                GuardedCallGraphs.graph(
                    entry,
                    GuardedCallGraphs.plain(1),
                    GuardedCallGraphs.plain(GuardedCallGraphs.EXIT)
                )
            )
        )
    )
        .isEmpty();
  }

  @Test
  void aCallWritesItsBareArgumentCleanAfterTheCall() {
    final Map<String, LocalMethod> methods =
        Map.of(
            "check/1",
            GuardedCallGraphs.method(
                GuardedCallGraphs.edge(GuardedCallGraphs.EXIT, FlowEdgeKind.EXCEPTION),
                List.of()
            )
        );
    final ControlFlowGraph caller = GuardedCallGraphs.caller(
        GuardedCallGraphs.CALL,
        GuardedCallGraphs.ARGUMENT
    );

    final ControlFlowGraph guarded = GuardedCalls.over(methods).apply(caller);

    final List<VariableAccess> accesses = guarded.blocks().get(0).accesses();
    assertThat(accesses).hasSize(2);
    assertThat(accesses.get(1).kind()).isEqualTo(AccessKind.WRITE);
    assertThat(accesses.get(1).name()).isEqualTo("x");
  }

  @Test
  void anArgumentThatIsNotAVariableStaysAsItIs() {
    final Map<String, LocalMethod> methods =
        Map.of(
            "check/1",
            GuardedCallGraphs.method(
                GuardedCallGraphs.edge(GuardedCallGraphs.EXIT, FlowEdgeKind.EXCEPTION),
                List.of()
            )
        );
    final ControlFlowGraph caller = GuardedCallGraphs.caller(
        GuardedCallGraphs.CALL,
        new Span(5, 7, 5, 12, 60, 65)
    );

    assertThat(GuardedCalls.over(methods).apply(caller)).isSameAs(caller);
  }

  @Test
  void withoutACheckingMethodTheGraphStaysAsItIs() {
    final Map<String, LocalMethod> methods =
        Map.of(
            "check/1",
            GuardedCallGraphs.method(
                GuardedCallGraphs.edge(GuardedCallGraphs.EXIT, FlowEdgeKind.NORMAL),
                List.of()
            )
        );
    final ControlFlowGraph caller = GuardedCallGraphs.caller(
        GuardedCallGraphs.CALL,
        GuardedCallGraphs.ARGUMENT
    );

    assertThat(GuardedCalls.over(methods).apply(caller)).isSameAs(caller);
  }
}
