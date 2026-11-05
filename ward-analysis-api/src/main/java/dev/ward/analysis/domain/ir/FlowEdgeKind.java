package dev.ward.analysis.domain.ir;

/** Why control moves from one basic block to another. */
public enum FlowEdgeKind {
  /** Ordinary sequential flow, a jump, a loop back edge or a fall-through. */
  NORMAL,
  /** The condition of the source block evaluated to true. */
  TRUE,
  /** The condition of the source block evaluated to false. */
  FALSE,
  /** A statement that can throw moves control to a handler or to the exit. */
  EXCEPTION
}
