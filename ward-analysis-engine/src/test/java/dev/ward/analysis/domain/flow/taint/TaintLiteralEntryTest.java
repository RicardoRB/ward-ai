package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Checks that a literal entry matches literal sites by their contents. */
class TaintLiteralEntryTest {

  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  /** A literal entry with a pattern matches only a literal that the pattern finds. */
  @Test
  void literalEntryMatchesOnlyALiteralSiteWhosePatternFinds() {
    final TaintEntry entry = literalEntry(Optional.of(Pattern.compile(".+")));

    assertThat(entry.matches(site(FlowSiteKind.LITERAL, "secret"))).isTrue();
    assertThat(entry.matches(site(FlowSiteKind.LITERAL, ""))).isFalse();
    assertThat(entry.matches(site(FlowSiteKind.CALL, "secret"))).isFalse();
  }

  /** A literal entry without a pattern matches every literal. */
  @Test
  void literalEntryWithoutAPatternMatchesEveryLiteral() {
    assertThat(literalEntry(Optional.empty()).matches(site(FlowSiteKind.LITERAL, ""))).isTrue();
  }

  private static TaintEntry literalEntry(final Optional<Pattern> pattern) {
    return new TaintEntry(
        FlowSiteKind.LITERAL, "", Optional.empty(), Optional.empty(), List.of(), pattern);
  }

  private static FlowSite site(final FlowSiteKind kind, final String name) {
    return new FlowSite(kind, name, "value", Optional.empty(), List.of(), List.of(), SPAN);
  }
}
