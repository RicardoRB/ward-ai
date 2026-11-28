package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What files of one run share of the work on the methods they reach. */
class SummaryMemoTest {
  private static final int CALLEE = 10;

  @Test
  void filesThatReachTheSameMethodAnalyseItOnceAndGetTheSameSummary() {
    final SummaryMemo memo = SummaryMemo.shared();
    final List<ControlFlowGraph> related = List.of(TaintFileSummaryTest.getter("source", CALLEE));

    final String first = TaintFileSummary.of(
        List.of(TaintFileSummaryTest.caller(0)),
        related,
        List.of(TaintFileSummaryTest.SPEC),
        memo
    );
    final int afterFirst = memo.size();
    final String second = TaintFileSummary.of(
        List.of(TaintFileSummaryTest.caller(40)),
        related,
        List.of(TaintFileSummaryTest.SPEC),
        memo
    );

    assertThat(afterFirst).isPositive();
    assertThat(memo.size()).isEqualTo(afterFirst);
    assertThat(second).isEqualTo(first).isEqualTo(
        TaintFileSummary.of(
            List.of(TaintFileSummaryTest.caller(0)),
            related,
            List.of(TaintFileSummaryTest.SPEC),
            SummaryMemo.disabled()
        )
    );
  }

  @Test
  void aMethodWithOtherMatchingEntriesIsAnalysedAgainAndNotTakenFromTheMemo() {
    final SummaryMemo memo = SummaryMemo.shared();
    final List<ControlFlowGraph> related = List.of(TaintFileSummaryTest.getter("source", CALLEE));
    final TaintSpec other =
        new TaintSpec(
            List.of(TaintFileSummaryTest.entry("other", Optional.empty(), List.of())),
            List.of(TaintFileSummaryTest.entry("sink", Optional.empty(), List.of(0))),
            List.of(),
            List.of()
        );

    TaintFileSummary.of(
        List.of(TaintFileSummaryTest.caller(0)),
        related,
        List.of(TaintFileSummaryTest.SPEC),
        memo
    );

    assertThat(
        TaintFileSummary.of(List.of(TaintFileSummaryTest.caller(0)), related, List.of(other), memo)
    )
        .isEqualTo(
            TaintFileSummary.of(
                List.of(TaintFileSummaryTest.caller(0)),
                related,
                List.of(other),
                SummaryMemo.disabled()
            )
        )
        .isEmpty();
  }
}
