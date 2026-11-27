package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemberCollectionsTest {
  private static final TaintSpec SPEC = new TaintSpec(List.of(), List.of(), List.of(), List.of());

  private static boolean holds(final MemberVariant variant) {
    final MemberGraphs.Scenario scenario = MemberGraphs.scenario(variant);
    return MemberCollections.holds(
        scenario.methods().get("lookup/1"),
        scenario.methods(),
        scenario.universe(),
        SPEC
    );
  }

  @Test
  void aLookupThatGivesAValueOnlyForAMemberOfTheGettersCollectionHolds() {
    assertThat(holds(MemberVariant.proven())).isTrue();
  }

  @Test
  void aLookupWhoseReturnsAreNotMembersDoesNotHold() {
    assertThat(holds(MemberVariant.proven().withoutMember())).isFalse();
  }

  @Test
  void aLookupWithAReturnOfAnotherKindDoesNotHold() {
    assertThat(holds(MemberVariant.proven().withOtherReturn())).isFalse();
  }

  @Test
  void aCollectionLocalThatAClosureCapturesDoesNotHold() {
    assertThat(holds(MemberVariant.proven().withHandedOnAlias())).isFalse();
  }

  @Test
  void aKeyThatIsAssignedAgainDoesNotHold() {
    assertThat(holds(MemberVariant.proven().withSecondAssignment())).isFalse();
  }

  @Test
  void aGetterThatNoMethodAnswersDoesNotHold() {
    final MemberGraphs.Scenario scenario = MemberGraphs.scenario(MemberVariant.proven());
    final Map<String, LocalMethod> without = Map.of("lookup/1", scenario.methods().get("lookup/1"));

    assertThat(MemberCollections.holds(without.get("lookup/1"), without, scenario.universe(), SPEC))
        .isFalse();
  }

  @Test
  void aCallerThatKeepsTheCollectionOfTheGetterDoesNotHold() {
    final MemberGraphs.Scenario scenario = MemberGraphs.scenario(MemberVariant.proven());
    final List<ControlFlowGraph> universe = new ArrayList<>(scenario.universe());
    universe.add(MemberGraphs.stray());

    assertThat(
        MemberCollections.holds(
            scenario.methods().get("lookup/1"),
            scenario.methods(),
            universe,
            SPEC
        )
    ).isFalse();
  }

  @Test
  void aMethodThatIsNotALookupDoesNotHold() {
    final MemberGraphs.Scenario scenario = MemberGraphs.scenario(MemberVariant.proven());

    assertThat(
        MemberCollections.holds(
            scenario.methods().get("load/0"),
            scenario.methods(),
            scenario.universe(),
            SPEC
        )
    ).isFalse();
  }
}
