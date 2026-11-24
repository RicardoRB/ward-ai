package dev.ward.analysis.domain.flow.taint;

import static dev.ward.analysis.domain.flow.taint.GuardedGraphs.END;
import static dev.ward.analysis.domain.flow.taint.GuardedGraphs.RESPONSE;
import static dev.ward.analysis.domain.flow.taint.GuardedGraphs.STRINGIFY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.GuardedGraphs.Fixture;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The argument of a sink whose call is listed as data once the response has a JSON type. */
class GuardedArgumentTest {
  private static final int EARLY = 10;
  private static final int LATE = 200;
  private static final TaintEntry SINK =
      new TaintEntry(
          FlowSiteKind.CALL,
          "end",
          Optional.empty(),
          Optional.of(RESPONSE),
          List.of(0),
          Optional.empty(),
          List.of("JSON.stringify")
      );

  @Test
  void aTypeSetEarlierInTheBlockMakesTheCallData() {
    final Fixture fixture =
        GuardedGraphs.straight(List.of(GuardedGraphs.typed(RESPONSE, EARLY), STRINGIFY, END));

    assertThat(clean(fixture)).isNotEmpty();
  }

  @Test
  void aTypeSetAfterTheSinkOrOnAnotherResponseDoesNot() {
    final Fixture after =
        GuardedGraphs.straight(List.of(STRINGIFY, END, GuardedGraphs.typed(RESPONSE, LATE)));
    final Fixture other =
        GuardedGraphs.straight(List.of(GuardedGraphs.typed("other", EARLY), STRINGIFY, END));

    assertThat(clean(after)).isEmpty();
    assertThat(clean(other)).isEmpty();
  }

  @Test
  void aTypeInADominatingBlockCountsButOneOnABranchDoesNot() {
    assertThat(clean(GuardedGraphs.dominating())).isNotEmpty();
    assertThat(clean(GuardedGraphs.oneBranch())).isEmpty();
  }

  @Test
  void aCallChainedOnTheJsonCallIsNotTheWholeArgument() {
    final FlowSite chained = GuardedGraphs.call(
        "toUpperCase",
        "JSON.stringify(x)",
        GuardedGraphs.span(100, 125)
    );
    final Fixture fixture =
        GuardedGraphs.straight(
            List.of(GuardedGraphs.typed(RESPONSE, EARLY), STRINGIFY, chained, END)
        );

    assertThat(clean(fixture)).isEmpty();
  }

  @Test
  void aSinkWithoutListedCallsHasNoCleanPart() {
    final TaintEntry plain =
        new TaintEntry(
            FlowSiteKind.CALL,
            "end",
            Optional.empty(),
            Optional.of(RESPONSE),
            List.of(0)
        );
    final Fixture fixture =
        GuardedGraphs.straight(List.of(GuardedGraphs.typed(RESPONSE, EARLY), STRINGIFY, END));

    final GuardedArgument argument =
        GuardedArgument.of(
            new TaintState(fixture.graph(), null, Map.of()),
            fixture.sink(),
            END,
            GuardedGraphs.ARGUMENT,
            plain
        );

    assertThat(argument.clean()).isEmpty();
    assertThat(argument.isClean(GuardedGraphs.INSIDE)).isFalse();
  }

  private static List<Span> clean(final Fixture fixture) {
    return GuardedArgument.of(
        new TaintState(fixture.graph(), null, Map.of()),
        fixture.sink(),
        END,
        GuardedGraphs.ARGUMENT,
        SINK
    ).clean();
  }
}
