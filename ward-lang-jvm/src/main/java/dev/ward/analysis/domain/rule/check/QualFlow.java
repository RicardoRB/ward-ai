package dev.ward.analysis.domain.rule.check;

/**
 * What a flow of values knows about a type qualifier: always present, never present or unknown. A
 * value a flow says nothing about has no entry, which stands for the top of the lattice.
 */
enum QualFlow {
  /** Always has the qualifier. */
  ALWAYS,
  /** Never has it. */
  NEVER,
  /** May or may not. */
  UNKNOWN;

  /** Returns the flow that a {@code when} stands for. */
  static QualFlow of(final QualWhen when) {
    return switch (when) {
      case ALWAYS -> ALWAYS;
      case NEVER -> NEVER;
      default -> UNKNOWN;
    };
  }

  /** Joins two flows: equal flows stay, different flows become unknown. */
  static QualFlow meet(final QualFlow left, final QualFlow right) {
    return left == right ? left : UNKNOWN;
  }

  /** Tells whether what flows forward conflicts with what the code later requires. */
  static boolean conflict(final boolean strict, final QualFlow forward, final QualFlow backward) {
    if (forward == null || backward == null || backward == UNKNOWN || forward == backward) {
      return false;
    }
    return strict
        || forward == ALWAYS && backward == NEVER
        || forward == NEVER && backward == ALWAYS;
  }

  /** Tells whether a source of {@code when} cannot satisfy a requirement of {@code needed}. */
  static boolean sourceConflicts(final boolean strict, final QualFlow needed, final QualWhen when) {
    if (strict) {
      return needed == ALWAYS && when != QualWhen.ALWAYS
          || needed == NEVER && when != QualWhen.NEVER;
    }
    return needed == ALWAYS && (when == QualWhen.NEVER || when == QualWhen.MAYBE)
        || needed == NEVER && (when == QualWhen.ALWAYS || when == QualWhen.MAYBE);
  }
}
