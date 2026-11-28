package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** When a method keeps its result from an earlier round of its file. */
class RoundCoresTest {
  private static final SummaryMemo.Core CORE = new SummaryMemo.Core(
      Optional.empty(),
      Map.of(),
      true
  );

  private static LocalMethod getterOf(final String inner) {
    return LocalMethod.index(List.of(TaintFileSummaryTest.getter(inner, 0)))
        .values()
        .iterator()
        .next();
  }

  @Test
  void aMethodIsAnalysedInTheFirstRound() {
    final LocalMethod method = getterOf("source");
    final RoundCores cores = new RoundCores(Map.of());

    assertThat(cores.reuse(method)).isEmpty();
    cores.keep(method, CORE);
    assertThat(cores.reuse(method)).isEmpty();
  }

  @Test
  void aMethodKeepsItsResultWhenNoChangedNameIsCalledByIt() {
    final LocalMethod method = getterOf("source");
    final RoundCores cores = new RoundCores(Map.of());
    cores.keep(method, CORE);

    cores.changed(Set.of("other"));
    assertThat(cores.reuse(method)).contains(CORE);
    cores.changed(Set.of());
    assertThat(cores.reuse(method)).contains(CORE);
  }

  @Test
  void aMethodIsAnalysedAgainWhenItCallsAChangedName() {
    final LocalMethod method = getterOf("source");
    final RoundCores cores = new RoundCores(Map.of());
    cores.keep(method, CORE);

    cores.changed(Set.of("source", "other"));

    assertThat(cores.reuse(method)).isEmpty();
  }

  @Test
  void theChangedNamesAreThoseOfTheMethodsWhoseSummaryDiffers() {
    final LocalMethod method = getterOf("source");
    final MethodSummary summary =
        new MethodSummary(true, Set.of(), false, Map.of(), List.of(), List.of());

    assertThat(SummaryRounds.changed(Map.of(method.key(), method), Map.of(), Map.of()))
        .isEmpty();
    assertThat(
        SummaryRounds.changed(Map.of(method.key(), method), Map.of(), Map.of(method.key(), summary))
    )
        .containsExactly("get");
    assertThat(
        SummaryRounds.changed(
            Map.of(method.key(), method),
            Map.of(method.key(), summary),
            Map.of(method.key(), summary)
        )
    )
        .isEmpty();
  }

  @Test
  void aMethodThatUsesAValueAValidatorProvedIsReachedByTheValidatorName() {
    final FlowSite use =
        new FlowSite(
            FlowSiteKind.VALIDATED,
            "url:isSafe@0",
            "",
            Optional.empty(),
            List.of(),
            List.of(),
            TaintFileSummaryTest.span(1)
        );
    final ControlFlowGraph graph =
        new ControlFlowGraph(
            "A#run(0)",
            TaintFileSummaryTest.span(0),
            List.of(
                new BasicBlock(0, List.of(), List.of(), List.of(), List.of(use)),
                new BasicBlock(1, List.of(), List.of(), List.of(), List.of())
            ),
            0,
            1
        );
    final LocalMethod method = LocalMethod.index(List.of(graph)).values().iterator().next();
    final RoundCores cores = new RoundCores(Map.of());
    cores.keep(method, CORE);

    cores.changed(Set.of("isSafe"));
    assertThat(cores.reuse(method)).isEmpty();
    cores.changed(Set.of("unrelated"));
    assertThat(cores.reuse(method)).contains(CORE);
  }
}
