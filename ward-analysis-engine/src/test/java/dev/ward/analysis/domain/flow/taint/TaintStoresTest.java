package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A container that a tainted value is stored into is tainted; a clean store leaves it clean. */
class TaintStoresTest {
  private static final TaintSpec SPEC =
      new TaintSpec(
          List.of(entry("source", List.of())),
          List.of(entry("sink", List.of(0))),
          List.of(),
          List.of(entry("add", List.of(0))));

  private static TaintEntry entry(final String name, final List<Integer> arguments) {
    return new TaintEntry(FlowSiteKind.CALL, name, Optional.empty(), Optional.empty(), arguments);
  }

  private static List<TaintFinding> run(final FlowSite store, final boolean tainted) {
    return TaintAnalysis.run(TaintStoreGraphs.graph(store, tainted), SPEC);
  }

  @Test
  void aTaintedValueStoredIntoAMemberTaintsTheVariable() {
    final List<TaintFinding> found = run(TaintStoreGraphs.memberWrite("c"), true);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).path().get(1).message()).isEqualTo("stored into c");
  }

  @Test
  void aTaintedValueGivenToAPropagatorCallTaintsItsReceiver() {
    assertThat(run(TaintStoreGraphs.call("add", "c"), true)).hasSize(1);
  }

  @Test
  void aCleanValueLeavesTheContainerClean() {
    assertThat(run(TaintStoreGraphs.memberWrite("c"), false)).isEmpty();
    assertThat(run(TaintStoreGraphs.call("add", "c"), false)).isEmpty();
  }

  @Test
  void aTargetThatIsNotAPlainVariableIsNotAContainer() {
    assertThat(run(TaintStoreGraphs.memberWrite("this"), true)).isEmpty();
    assertThat(run(TaintStoreGraphs.call("add", "c.items"), true)).isEmpty();
  }

  @Test
  void aCallThatNoPropagatorNamesStoresNothing() {
    assertThat(run(TaintStoreGraphs.call("remove", "c"), true)).isEmpty();
  }
}
