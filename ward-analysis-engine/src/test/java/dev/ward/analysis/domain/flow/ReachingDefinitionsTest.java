package dev.ward.analysis.domain.flow;

import static dev.ward.analysis.domain.flow.GraphFixtures.graph;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.GraphFixtures.Block;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reaching definitions on hand-built graphs, one per scenario of the intraprocedural spec. */
class ReachingDefinitionsTest {

  private static final int THIRD_LINE = 3;

  @Test
  void twoBranchesReachTheReadAfterThem() {
    final ControlFlowGraph graph =
        graph(
            GraphFixtures.empty().write("x", 1).edge(2).edge(THIRD_LINE),
            GraphFixtures.empty(),
            new Block().write("x", 2).edge(4),
            new Block().write("x", THIRD_LINE).edge(4),
            new Block().read("x", 4));
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);
    final AccessSite read = reaching.uses().get(0);

    assertThat(lines(reaching.reaching(read))).containsExactly(2, THIRD_LINE);
  }

  @Test
  void aLaterWriteHidesTheEarlierOne() {
    final ControlFlowGraph graph =
        graph(
            new Block().write("x", 1).write("x", 2).read("x", THIRD_LINE).edge(1),
            GraphFixtures.empty());
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);

    assertThat(lines(reaching.reaching(reaching.uses().get(0)))).containsExactly(2);
  }

  @Test
  void aWriteInTheTryReachesTheCatchBlock() {
    // The try block may raise before its write, so the entry block has an exception edge too.
    final ControlFlowGraph graph =
        graph(
            GraphFixtures.empty().write("x", 1).edge(2).exception(THIRD_LINE),
            GraphFixtures.empty(),
            new Block().write("x", 2).exception(THIRD_LINE).edge(4),
            new Block().read("x", THIRD_LINE).edge(1),
            GraphFixtures.empty().edge(1));
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);

    assertThat(lines(reaching.reaching(reaching.uses().get(0)))).containsExactly(1, 2);
  }

  @Test
  void aLoopCarriesTheIncrementToItsCondition() {
    final ControlFlowGraph graph =
        graph(
            new Block().write("i", 1).edge(2),
            GraphFixtures.empty(),
            new Block().read("i", 2).edge(THIRD_LINE).edge(1),
            new Block().read("i", THIRD_LINE).write("i", THIRD_LINE).edge(2));
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);
    final AccessSite condition = reaching.uses().get(0);
    final AccessSite increment = reaching.uses().get(1);

    assertThat(lines(reaching.reaching(condition))).containsExactly(1, THIRD_LINE);
    assertThat(lines(reaching.reaching(increment))).containsExactly(1, THIRD_LINE);
  }

  @Test
  void aWriteThatReachesItselfThroughALoopIsNotOverwrittenByItself() {
    final ControlFlowGraph graph =
        graph(
            new Block().edge(2),
            GraphFixtures.empty(),
            new Block().write("x", THIRD_LINE).edge(2).edge(1));
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);

    assertThat(reaching.overwritten(reaching.definitions().get(0))).isFalse();
  }

  @Test
  void aWriteIsOverwrittenByAnotherWriteThatItReaches() {
    final ControlFlowGraph graph =
        graph(
            new Block().write("x", 1).edge(2),
            GraphFixtures.empty(),
            new Block().write("x", 2).edge(1));
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);

    assertThat(reaching.overwritten(reaching.definitions().get(0))).isTrue();
    assertThat(reaching.overwritten(reaching.definitions().get(1))).isFalse();
  }

  @Test
  void parameterIsAWriteAtTheEntry() {
    final ControlFlowGraph graph =
        graph(new Block().parameter("a", 1).read("a", 2).edge(1), GraphFixtures.empty());
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);

    assertThat(reaching.definitions()).hasSize(1);
    assertThat(lines(reaching.reaching(reaching.uses().get(0)))).containsExactly(1);
  }

  @Test
  void aWriteDoesNotReachAReadOfAnotherVariable() {
    final ControlFlowGraph graph =
        graph(new Block().write("x", 1).read("y", 2).edge(1), GraphFixtures.empty());
    final ReachingDefinitions reaching = ReachingDefinitions.of(graph);

    assertThat(reaching.reaching(reaching.uses().get(0))).isEmpty();
  }

  @Test
  void theResultIsTheSameForTheSameGraph() {
    final ControlFlowGraph graph =
        graph(
            new Block().write("x", 1).edge(2).edge(THIRD_LINE),
            GraphFixtures.empty(),
            new Block().write("x", 2).edge(4),
            new Block().write("x", THIRD_LINE).edge(4),
            new Block().read("x", 4));

    assertThat(ReachingDefinitions.of(graph).uses())
        .isEqualTo(ReachingDefinitions.of(graph).uses());
    assertThat(DefUseChains.of(graph).definitions())
        .isEqualTo(DefUseChains.of(graph).definitions());
  }

  private static List<Integer> lines(final List<AccessSite> sites) {
    return sites.stream().map(site -> site.access().span().startLine()).toList();
  }
}
