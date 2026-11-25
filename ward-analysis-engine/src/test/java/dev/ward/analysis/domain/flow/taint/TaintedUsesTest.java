package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintedUses.Found;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What the uses of a source are, whether or not they reach a sink. */
class TaintedUsesTest {
  private static final int LINE = 9;
  private static final int START = 99;
  private static final Span ELSEWHERE = new Span(LINE, 1, LINE, 2, START, START + 1);
  private static final Span SOURCE_CALL = TaintStoreGraphs.VALUE;
  private static final TaintSpec SPEC =
      new TaintSpec(List.of(entry("source")), List.of(), List.of(), List.of(entry("add")));

  private static TaintEntry entry(final String name) {
    return new TaintEntry(FlowSiteKind.CALL, name, Optional.empty(), Optional.empty(), List.of(0));
  }

  private static Found found(final List<TaintEntry> extra, final Optional<Span> only) {
    final ControlFlowGraph graph =
        TaintStoreGraphs.graph(TaintStoreGraphs.call("add", "c"), true);
    return TaintedUses.of(List.of(graph), List.of(), SPEC, extra, only);
  }

  @Test
  void aSourceIsAnOriginAndTheVariablesItTaintsAreRead() {
    final Found found = found(List.of(), Optional.empty());

    assertThat(found.origins()).hasSize(1);
    assertThat(found.origins().get(0).derived()).isFalse();
    assertThat(found.origins().get(0).site().span()).isEqualTo(SOURCE_CALL);
    assertThat(found.reads()).isNotEmpty();
    assertThat(found.reads()).allSatisfy(read -> assertThat(read.origin()).isEqualTo(SOURCE_CALL));
  }

  @Test
  void theOnlyOriginThatIsChosenIsTheOneThatIsFollowed() {
    assertThat(found(List.of(), Optional.of(SOURCE_CALL)).origins()).hasSize(1);
    assertThat(found(List.of(), Optional.of(ELSEWHERE)).origins()).isEmpty();
  }

  @Test
  void anExtraSourceIsFollowedWhateverTheChoice() {
    final Found found = found(List.of(entry("sink")), Optional.of(ELSEWHERE));

    assertThat(found.origins()).hasSize(1);
    assertThat(found.origins().get(0).derived()).isTrue();
    assertThat(found.origins().get(0).site().name()).isEqualTo("sink");
  }
}
