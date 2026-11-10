package dev.ward.analysis.domain.flow.taint;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Checks the matching rules that design.md gives for taint specification entries. */
class TaintEntryTest {

  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);
  private static final String QUERY = "query";

  /** A name must be equal, whatever the receiver. */
  @Test
  void nameMustBeEqual() {
    assertThat(
            entry("executeQuery", Optional.empty(), Optional.empty())
                .matches(call("execute", Optional.of("java.sql.Statement"), "st")))
        .isFalse();
  }

  /** When both the entry and the site have a receiver type, the types must be equal. */
  @Test
  void receiverTypeMustBeEqualWhenTheSiteHasOne() {
    final TaintEntry entry =
        entry("executeQuery", Optional.of("java.sql.Statement"), Optional.empty());
    assertThat(entry.matches(call("executeQuery", Optional.of("java.sql.Statement"), "st")))
        .isTrue();
    assertThat(entry.matches(call("executeQuery", Optional.of("org.Other"), "st"))).isFalse();
  }

  /** A site without a receiver type matches a receiver entry only through its qualifier. */
  @Test
  void unresolvedReceiverNeverMatchesByAccident() {
    final TaintEntry byReceiverOnly = entry(QUERY, Optional.of("pg.Pool"), Optional.empty());
    assertThat(byReceiverOnly.matches(call(QUERY, Optional.empty(), "db"))).isFalse();

    final TaintEntry byQualifier = entry(QUERY, Optional.of("pg.Pool"), Optional.of("db"));
    assertThat(byQualifier.matches(call(QUERY, Optional.empty(), "db"))).isTrue();
    assertThat(byQualifier.matches(call(QUERY, Optional.empty(), "other"))).isFalse();
  }

  /** An entry without a receiver matches by name, and by qualifier when it names one. */
  @Test
  void entryWithoutReceiverMatchesByNameAndQualifier() {
    assertThat(
            entry("sink", Optional.empty(), Optional.empty())
                .matches(call("sink", Optional.empty(), "")))
        .isTrue();
    assertThat(
            entry("sink", Optional.empty(), Optional.of("db"))
                .matches(call("sink", Optional.empty(), "db")))
        .isTrue();
    assertThat(
            entry("sink", Optional.empty(), Optional.of("db"))
                .matches(call("sink", Optional.empty(), "x")))
        .isFalse();
  }

  /** A site of another kind never matches, so a call entry does not match a member read. */
  @Test
  void kindMustMatch() {
    final FlowSite read =
        new FlowSite(
            FlowSiteKind.MEMBER_READ, QUERY, "req", Optional.empty(), List.of(), List.of(), SPAN);
    assertThat(entry(QUERY, Optional.empty(), Optional.empty()).matches(read)).isFalse();
    assertThat(
            new TaintEntry(
                    FlowSiteKind.MEMBER_READ,
                    QUERY,
                    Optional.empty(),
                    Optional.of("req"),
                    List.of())
                .matches(read))
        .isTrue();
  }

  /** A parameter entry matches an annotation of the same name. */
  @Test
  void parameterEntryMatchesAnnotation() {
    final TaintEntry entry =
        new TaintEntry(
            FlowSiteKind.PARAMETER, "Untrusted", Optional.empty(), Optional.empty(), List.of());
    final FlowSite parameter =
        new FlowSite(
            FlowSiteKind.PARAMETER,
            "input",
            "",
            Optional.empty(),
            List.of("Untrusted"),
            List.of(),
            SPAN);
    final FlowSite plain =
        new FlowSite(
            FlowSiteKind.PARAMETER,
            "input",
            "",
            Optional.empty(),
            List.of("Other"),
            List.of(),
            SPAN);
    assertThat(entry.matches(parameter)).isTrue();
    assertThat(entry.matches(plain)).isFalse();
  }

  /** Joining specifications keeps every entry in file order. */
  @Test
  void combineKeepsEntriesInOrder() {
    final TaintEntry first = entry("a", Optional.empty(), Optional.empty());
    final TaintEntry second = entry("b", Optional.empty(), Optional.empty());
    final TaintSpec combined =
        TaintSpec.combine(
            List.of(
                new TaintSpec(List.of(first), List.of(), List.of(), List.of()),
                new TaintSpec(List.of(second), List.of(), List.of(), List.of())));
    assertThat(combined.sources()).containsExactly(first, second);
  }

  private static TaintEntry entry(
      final String name, final Optional<String> receiver, final Optional<String> qualifier) {
    return new TaintEntry(FlowSiteKind.CALL, name, receiver, qualifier, List.of());
  }

  private static FlowSite call(
      final String name, final Optional<String> receiverType, final String qualifier) {
    return new FlowSite(
        FlowSiteKind.CALL, name, qualifier, receiverType, List.of(), List.of(), SPAN);
  }
}
