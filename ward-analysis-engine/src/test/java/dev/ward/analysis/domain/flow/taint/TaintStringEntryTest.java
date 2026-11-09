package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Checks that a string entry matches string-building sites by their literal text. */
class TaintStringEntryTest {

  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  /** A string entry matches a string-building site whose literal text the pattern finds. */
  @Test
  void stringEntryMatchesOnlyAStringSiteWhosePatternFinds() {
    final TaintEntry entry =
        new TaintEntry(
            FlowSiteKind.STRING_BUILD,
            "",
            Optional.empty(),
            Optional.empty(),
            List.of(),
            Optional.of(Pattern.compile("<[a-zA-Z]")));

    assertThat(entry.matches(stringBuild("<p></p>"))).isTrue();
    assertThat(entry.matches(stringBuild("id: "))).isFalse();
    assertThat(entry.matches(call())).isFalse();
  }

  private static FlowSite stringBuild(final String literal) {
    return new FlowSite(
        FlowSiteKind.STRING_BUILD, literal, "", Optional.empty(), List.of(), List.of(), SPAN);
  }

  private static FlowSite call() {
    return new FlowSite(FlowSiteKind.CALL, "<p>", "", Optional.empty(), List.of(), List.of(), SPAN);
  }
}
