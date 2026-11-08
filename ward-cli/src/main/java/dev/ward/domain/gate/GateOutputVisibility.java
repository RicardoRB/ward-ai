package dev.ward.domain.gate;

/** Determines which captured output, if any, belongs in a rendered gate report. */
public enum GateOutputVisibility {
  NONE,
  FINDINGS,
  TAIL,
  FULL;

  /**
   * Returns what to show for {@code result}: everything at info level, nothing for a passed gate,
   * findings when some are visible, and otherwise the output tail. A gate that could not analyze
   * everything shows its findings and its output, which names what failed.
   */
  public static GateOutputVisibility of(
      final GateResult result, final SeverityThreshold threshold) {
    if (threshold.showsCapturedOutput()) {
      return FULL;
    }
    if (result.passed()) {
      return NONE;
    }
    if (result.findings().visible(threshold).items().isEmpty()) {
      return TAIL;
    }
    return result.status() == GateStatus.EXECUTION_ERROR ? FULL : FINDINGS;
  }
}
