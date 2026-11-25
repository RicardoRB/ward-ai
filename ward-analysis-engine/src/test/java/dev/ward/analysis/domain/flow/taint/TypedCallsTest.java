package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A method that sets the content type of its parameter types the response of its callers. */
class TypedCallsTest {
  private static final VariableAccess PARAMETER =
      new VariableAccess(
          "p",
          AccessKind.PARAMETER,
          GuardedCallGraphs.DECLARATION,
          GuardedCallGraphs.DECLARATION
      );

  @Test
  void aMethodThatTypesItsParameterOnEveryPathTypesIt() {
    final LocalMethod method = method(List.of(PARAMETER), false);

    assertThat(TypedCalls.typedParameters(method)).containsEntry(0, Set.of("nosniff", "plain"));
  }

  @Test
  void aTypeThatAPathCanSkipOrAReassignedParameterTypesNothing() {
    final VariableAccess assigned =
        new VariableAccess(
            "p",
            AccessKind.WRITE,
            GuardedCallGraphs.ARGUMENT,
            GuardedCallGraphs.DECLARATION
        );

    assertThat(TypedCalls.typedParameters(method(List.of(PARAMETER), true))).isEmpty();
    assertThat(TypedCalls.typedParameters(method(List.of(PARAMETER, assigned), false))).isEmpty();
  }

  @Test
  void aCallGetsTheSitesOfTheTypesOnItsArgument() {
    final LocalMethod method = method(List.of(PARAMETER), false);
    final ControlFlowGraph caller = GuardedCallGraphs.caller(
        GuardedCallGraphs.CALL,
        GuardedCallGraphs.ARGUMENT
    );

    final ControlFlowGraph typed = TypedCalls.over(Map.of(method.key(), method)).apply(caller);

    assertThat(typed.blocks().get(0).sites())
        .filteredOn(site -> site.kind() == FlowSiteKind.CONTENT_TYPE)
        .extracting(FlowSite::name, FlowSite::qualifier)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("plain", "x"),
            org.assertj.core.groups.Tuple.tuple("nosniff", "x")
        );
  }

  @Test
  void withoutATypingMethodTheGraphIsTheSame() {
    final LocalMethod method = method(List.of(PARAMETER), true);
    final ControlFlowGraph caller = GuardedCallGraphs.caller(
        GuardedCallGraphs.CALL,
        GuardedCallGraphs.ARGUMENT
    );

    assertThat(TypedCalls.over(Map.of(method.key(), method)).apply(caller)).isSameAs(caller);
  }

  /** The entry reaches the exit through the block that sets plain and nosniff on {@code p}. */
  private static LocalMethod method(final List<VariableAccess> accesses, final boolean skippable) {
    final BasicBlock entry =
        GuardedCallGraphs.block(
            0,
            skippable
                ? List.of(
                    GuardedCallGraphs.edge(1, FlowEdgeKind.TRUE),
                    GuardedCallGraphs.edge(GuardedCallGraphs.EXIT, FlowEdgeKind.FALSE)
                )
                : List.of(GuardedCallGraphs.edge(1, FlowEdgeKind.NORMAL)),
            accesses,
            skippable ? List.of() : types()
        );
    final BasicBlock middle =
        GuardedCallGraphs.block(
            1,
            List.of(GuardedCallGraphs.edge(GuardedCallGraphs.EXIT, FlowEdgeKind.NORMAL)),
            List.of(),
            skippable ? types() : List.of()
        );
    return GuardedCallGraphs.local(
        GuardedCallGraphs.graph(entry, middle, GuardedCallGraphs.plain(GuardedCallGraphs.EXIT))
    );
  }

  private static List<FlowSite> types() {
    return List.of(GuardedGraphs.typed("plain", "p", 1), GuardedGraphs.typed("nosniff", "p", 2));
  }
}
