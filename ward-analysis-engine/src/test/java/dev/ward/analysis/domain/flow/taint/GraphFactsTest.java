package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What a run reads once from each graph of another file. */
class GraphFactsTest {
  private static final int CALLEE = 10;

  @Test
  void aGraphOfTheRunHasItsFactsReadOnceAndSharedByEveryFile() {
    final SummaryMemo memo = SummaryMemo.shared();
    final ControlFlowGraph graph = TaintFileSummaryTest.getter("source", CALLEE);

    assertThat(memo.facts(graph)).isSameAs(memo.facts(graph));
    assertThat(memo.hashOf(graph)).isEqualTo(graph.hashCode());
    assertThat(memo.facts(graph).callKeys()).containsExactly("source/0");
    assertThat(memo.facts(graph).local()).hasValueSatisfying(
        method -> assertThat(method.nameKey()).isEqualTo("get/0")
    );
  }

  @Test
  void aDisabledMemoReadsTheFactsAgainAndGetsTheSameOnes() {
    final SummaryMemo memo = SummaryMemo.disabled();
    final ControlFlowGraph graph = TaintFileSummaryTest.getter("source", CALLEE);

    assertThat(memo.facts(graph)).isNotSameAs(memo.facts(graph)).isEqualTo(memo.facts(graph));
  }

  @Test
  void theContextOfAFileIsTheSameWithSharedAndWithDisabledFacts() {
    final List<ControlFlowGraph> own = List.of(TaintFileSummaryTest.caller(0));
    final List<ControlFlowGraph> related = List.of(TaintFileSummaryTest.getter("source", CALLEE));

    final TaintContext shared = TaintContext.of(own, related, SummaryMemo.shared());
    final TaintContext plain = TaintContext.of(own, related, SummaryMemo.disabled());

    assertThat(shared.known()).isEqualTo(plain.known());
    assertThat(shared.declared()).isEqualTo(plain.declared()).contains("get/0");
    assertThat(shared.index()).isEqualTo(plain.index());
    assertThat(shared.bound()).isEqualTo(plain.bound());
  }
}
