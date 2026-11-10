package dev.ward.analysis.domain.ir;

import dev.ward.analysis.domain.ir.ControlFlowFrames.Frame;
import dev.ward.analysis.domain.ir.ControlFlowFrames.Phase;

/**
 * Lowers try statements and the abrupt exits that leave them. A {@code finally} block is entered
 * from the normal end of the try and of each catch, and from every return, break, continue or
 * exception that leaves through it; its end continues each of those exits.
 */
public final class ControlFlowTries {

  private final ControlFlowBuilder builder;
  private final ControlFlowFrames frames;

  ControlFlowTries(final ControlFlowBuilder builder, final ControlFlowFrames frames) {
    this.builder = builder;
    this.frames = frames;
  }

  /**
   * Opens a try statement; its handler and finally blocks are created now.
   *
   * @param hasCatches whether the try has catch clauses
   * @param hasFinally whether the try has a finally block
   */
  public void pushTry(final boolean hasCatches, final boolean hasFinally) {
    final int handler = hasCatches ? builder.newBlock() : ControlFlowFrames.NO_BLOCK;
    final int finallyEntry = hasFinally ? builder.newBlock() : ControlFlowFrames.NO_BLOCK;
    frames.push(Frame.tryFrame(handler, finallyEntry));
    if (handler != ControlFlowFrames.NO_BLOCK) {
      // The body may raise before its first statement, so a catch is reachable from the block
      // before the try, which ends before the body's first statement. The body starts a block of
      // its own for that reason.
      final int body = builder.newBlock();
      builder.edge(builder.current(), handler, FlowEdgeKind.EXCEPTION);
      builder.edge(builder.current(), body, FlowEdgeKind.NORMAL);
      builder.moveTo(body);
    }
  }

  /** Ends the try body, which is the current block. Catch clauses, if any, come next. */
  public void endTryBody() {
    final Frame frame = frames.top();
    frame.addEnd(builder.current());
    frame.setPhase(Phase.CATCH);
  }

  /** Starts a catch clause, a block reached from the handler of the innermost try. */
  public void beginCatch() {
    final int entry = builder.newBlock();
    builder.edge(frames.top().getHandler(), entry, FlowEdgeKind.NORMAL);
    builder.moveTo(entry);
  }

  /** Ends the catch clause that is the current block. */
  public void endCatch() {
    frames.top().addEnd(builder.current());
  }

  /**
   * Starts the finally block of the innermost try, or the join block of a try without one. An
   * uncaught exception from the handler continues to the finally block or to the outer handler.
   */
  public void beginFinally() {
    final Frame frame = frames.top();
    if (frame.getHandler() != ControlFlowFrames.NO_BLOCK) {
      frames.propagate(frame.getHandler(), FlowEdgeKind.NORMAL);
    }
    frame.setPhase(Phase.FINALLY);
    final int target =
        frame.getFinallyEntry() == ControlFlowFrames.NO_BLOCK
            ? builder.newBlock()
            : frame.getFinallyEntry();
    for (final int end : frame.getEnds()) {
      builder.edge(end, target, FlowEdgeKind.NORMAL);
    }
    builder.moveTo(target);
  }

  /**
   * Closes the innermost try. Each jump or exception that left its body through the finally block
   * continues from the end of that block. The code after the try starts a block of its own, reached
   * from the end of the finally block by a normal edge.
   */
  public void popTry() {
    final Frame frame = frames.top();
    frames.pop();
    if (frame.getFinallyEntry() == ControlFlowFrames.NO_BLOCK) {
      return;
    }
    final int end = builder.current();
    for (final ControlFlowFrames.Continuation continuation : frame.getPending()) {
      if (continuation.raise()) {
        frames.propagate(end, FlowEdgeKind.EXCEPTION);
      } else {
        frames.routeJump(end, continuation.target(), continuation.depth());
      }
    }
    final int after = builder.newBlock();
    builder.edge(end, after, FlowEdgeKind.NORMAL);
    builder.moveTo(after);
  }

  /**
   * Returns from the body, through every enclosing finally block, and continues from a fresh block.
   */
  public void returnFrom() {
    frames.transfer(ControlFlowBuilder.EXIT, 0);
  }

  /** Throws from the current block to the innermost handler or to the exit, then continues. */
  public void throwOut() {
    frames.propagate(builder.current(), FlowEdgeKind.EXCEPTION);
    builder.moveTo(builder.newBlock());
  }
}
