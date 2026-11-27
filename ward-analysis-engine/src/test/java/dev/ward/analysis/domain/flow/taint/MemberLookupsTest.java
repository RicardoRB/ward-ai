package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class MemberLookupsTest {
  private static final int WITH_CLEAN = 4;
  private static final int WITHOUT_CLEAN = 3;
  private static final TaintSpec SPEC = new TaintSpec(List.of(), List.of(), List.of(), List.of());

  private static ControlFlowGraph checked(final MemberVariant variant) {
    final MemberGraphs.Scenario scenario = MemberGraphs.scenario(variant);
    final UnaryOperator<ControlFlowGraph> lookups =
        MemberLookups.over(
            scenario.methods(),
            List.of(scenario.caller()),
            scenario.universe(),
            SPEC
        );
    return lookups.apply(scenario.caller());
  }

  @Test
  void aProvedLookupWritesTheKeyCleanAtTheStartOfTheCheckBlock() {
    final ControlFlowGraph graph = checked(MemberVariant.proven());
    final VariableAccess first = graph.blocks().get(0).accesses().get(0);

    assertThat(first.name()).isEqualTo(MemberGraphs.KEY);
    assertThat(first.kind()).isEqualTo(AccessKind.WRITE);
    assertThat(graph.blocks().get(0).accesses()).hasSize(WITH_CLEAN);
  }

  @Test
  void aLookupThatIsNotProvedLeavesTheCallerAsItIs() {
    final MemberGraphs.Scenario scenario = MemberGraphs.scenario(
        MemberVariant.proven().withoutMember()
    );
    final ControlFlowGraph graph = checked(MemberVariant.proven().withoutMember());

    assertThat(graph.blocks().get(0).accesses()).hasSize(WITHOUT_CLEAN);
    assertThat(scenario.caller().blocks().get(0).accesses()).hasSize(WITHOUT_CLEAN);
  }

  @Test
  void graphsWithoutANullCheckMarkAreLeftAlone() {
    final MemberGraphs.Scenario scenario = MemberGraphs.scenario(MemberVariant.proven());
    final UnaryOperator<ControlFlowGraph> lookups =
        MemberLookups.over(
            java.util.Map.of(),
            List.of(scenario.getter()),
            scenario.universe(),
            SPEC
        );

    assertThat(lookups.apply(scenario.getter())).isSameAs(scenario.getter());
  }

  @Test
  void onlyTheGraphWithTheMarkMayVouch() {
    final MemberGraphs.Scenario scenario = MemberGraphs.scenario(MemberVariant.proven());

    assertThat(MemberVouching.mayVouch(List.of(scenario.caller()))).isTrue();
    assertThat(MemberVouching.mayVouch(List.of(scenario.lookup(), scenario.getter()))).isFalse();
  }

  @Test
  void assignmentsCountWritesAndTheParameterEntry() {
    final MemberGraphs.Scenario scenario =
        MemberGraphs.scenario(MemberVariant.proven().withSecondAssignment());

    assertThat(MemberLookups.assignments(scenario.lookup(), MemberGraphs.KEY)).isEqualTo(2);
    assertThat(MemberLookups.assignments(scenario.lookup(), MemberGraphs.KNOWN)).isEqualTo(1);
  }
}
