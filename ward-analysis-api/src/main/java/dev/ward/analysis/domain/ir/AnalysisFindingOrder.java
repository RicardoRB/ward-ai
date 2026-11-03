package dev.ward.analysis.domain.ir;

import java.util.Comparator;

/** The order of analysis findings in every report: file, line, column, rule, then message. */
public final class AnalysisFindingOrder {

  /** Sorts findings so that the same input always gives the same output. */
  public static final Comparator<AnalysisFinding> ORDER =
      Comparator.comparing(AnalysisFinding::file, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(AnalysisFinding::line, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(AnalysisFinding::column, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(AnalysisFinding::ruleId)
          .thenComparing(AnalysisFinding::message);

  private AnalysisFindingOrder() {}
}
