package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import java.util.List;

/** Builds the finding of a function whose CRAP score is above the limit. */
final class CrapFindings {
  /** The rule id of the findings. */
  static final String RULE = "crap";

  private static final String TOOL = "ward";

  private CrapFindings() {}

  /** Returns the error finding for {@code function} of {@code unit}, from the gate {@code gate}. */
  static Finding of(
      final String gate,
      final int max,
      final SourceUnit unit,
      final CrapFunctions.Scored function) {
    final CrapScore score = function.score();
    return new Finding(
        gate,
        TOOL,
        RULE,
        FindingSeverity.ERROR,
        "CRAP score "
            + score.display()
            + " is above "
            + max
            + " (complexity "
            + score.complexity()
            + ", coverage "
            + score.coverageDisplay()
            + ")",
        unit.path(),
        function.span().startLine(),
        function.span().startColumn(),
        null,
        List.of());
  }
}
