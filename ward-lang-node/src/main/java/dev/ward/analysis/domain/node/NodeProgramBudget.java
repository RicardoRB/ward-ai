package dev.ward.analysis.domain.node;

/** The steps the program-data analysis may take for one sink, which bounds its work. */
final class NodeProgramBudget {
  private static final int STEPS = 200_000;

  private int remaining = STEPS;

  /** Counts one step. */
  boolean spend() {
    remaining--;
    return remaining >= 0;
  }
}
