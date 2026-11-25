package dev.ward.analysis.domain.flow.taint;

import static dev.ward.analysis.domain.flow.taint.GuardedGraphs.END;
import static dev.ward.analysis.domain.flow.taint.GuardedGraphs.RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.GuardedGraphs.Fixture;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The argument of a sink that a plain-text response with nosniff makes safe. */
class GuardedTextResponseTest {
  private static final int EARLY = 10;
  private static final int LATE = 200;

  @Test
  void plainTextWithNosniffMakesTheWholeArgumentClean() {
    final Fixture fixture = text(List.of(typed("plain", EARLY), typed("nosniff", EARLY + 1)));

    assertThat(textClean(fixture)).containsExactly(GuardedGraphs.ARGUMENT);
  }

  @Test
  void plainTextNeedsNosniffAndNoOtherTypeOfTheResponse() {
    assertThat(textClean(text(List.of(typed("plain", EARLY))))).isEmpty();
    assertThat(textClean(text(List.of(typed("nosniff", EARLY))))).isEmpty();
    assertThat(
        textClean(
            text(List.of(typed("plain", EARLY), typed("nosniff", EARLY + 1), typed("other", LATE)))
        )
    ).isEmpty();
    assertThat(
        textClean(
            text(List.of(typed("plain", EARLY), typed("nosniff", EARLY + 1), typed("json", LATE)))
        )
    ).isEmpty();
  }

  @Test
  void aTextResponseOfAnotherVariableDoesNotCount() {
    final Fixture fixture =
        text(
            List.of(
                GuardedGraphs.typed("plain", "other", EARLY),
                GuardedGraphs.typed("nosniff", "other", EARLY + 1)
            )
        );

    assertThat(textClean(fixture)).isEmpty();
  }

  private static FlowSite typed(final String kind, final int offset) {
    return GuardedGraphs.typed(kind, RESPONSE, offset);
  }

  private static Fixture text(final List<FlowSite> types) {
    final List<FlowSite> sites = new java.util.ArrayList<>(types);
    sites.add(END);
    return GuardedGraphs.straight(sites);
  }

  private static List<Span> textClean(final Fixture fixture) {
    final TaintEntry sink =
        new TaintEntry(
            FlowSiteKind.CALL,
            "end",
            Optional.empty(),
            Optional.of(RESPONSE),
            List.of(0),
            Optional.empty(),
            List.of(TaintEntry.TEXT_RESPONSE),
            List.of()
        );
    return GuardedArgument.of(
        new TaintState(fixture.graph(), null, Map.of()),
        fixture.sink(),
        END,
        GuardedGraphs.ARGUMENT,
        sink
    ).clean();
  }
}
