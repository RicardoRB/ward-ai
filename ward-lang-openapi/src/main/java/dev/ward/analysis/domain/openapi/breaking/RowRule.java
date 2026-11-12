package dev.ward.analysis.domain.openapi.breaking;

import java.util.List;
import java.util.function.Function;

/** A rule whose id and check are one row of a family table. */
record RowRule(String id, Function<DocumentPair, List<BreakingFinding>> check)
    implements BreakingRule {

  /** The prefix of every breaking-change rule id. */
  static final String PREFIX = "ward:openapi/";

  /** A rule for the oasdiff check {@code checkId}. */
  static RowRule of(
      final String checkId, final Function<DocumentPair, List<BreakingFinding>> check) {
    return new RowRule(PREFIX + checkId, check);
  }

  @Override
  public List<BreakingFinding> check(final DocumentPair pair) {
    return check.apply(pair);
  }
}
