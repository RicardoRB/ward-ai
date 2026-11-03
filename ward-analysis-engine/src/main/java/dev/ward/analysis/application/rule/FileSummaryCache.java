package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.util.Optional;

/** Keeps the summaries of source units between runs. */
public interface FileSummaryCache {

  /**
   * Returns the stored summary of the unit, or empty when none is stored or the stored one does not
   * match the unit.
   */
  Optional<FileSummary> read(SourceUnit unit);

  /** Stores the summary of the unit. */
  void write(SourceUnit unit, FileSummary summary);
}
