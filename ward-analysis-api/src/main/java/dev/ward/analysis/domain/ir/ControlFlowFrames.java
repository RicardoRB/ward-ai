package dev.ward.analysis.domain.ir;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The stack of enclosing loops, switches, labels and try statements of one body, and the rules that
 * route jumps and exceptions through them. The block builder, the jump view and the try view all
 * share one stack.
 */
final class ControlFlowFrames {

  static final int NO_BLOCK = -1;

  private final ControlFlowBuilder builder;
  private final List<Frame> frames = new ArrayList<>();

  ControlFlowFrames(final ControlFlowBuilder builder) {
    this.builder = builder;
    frames.add(Frame.function());
  }

  /** Pushes a frame and returns its depth. */
  int push(final Frame frame) {
    frames.add(frame);
    return frames.size() - 1;
  }

  /** Pops the innermost frame. */
  void pop() {
    frames.remove(frames.size() - 1);
  }

  Frame top() {
    return frames.get(frames.size() - 1);
  }

  int size() {
    return frames.size();
  }

  Frame at(final int depth) {
    return frames.get(depth);
  }

  /** Jumps to {@code target}, which lies outside the frames above {@code depth}, then continues. */
  void transfer(final int target, final int depth) {
    routeJump(builder.current(), target, depth);
    builder.moveTo(builder.newBlock());
  }

  /** Routes a jump from {@code from} through the finally blocks it leaves on its way out. */
  void routeJump(final int from, final int target, final int depth) {
    final Frame finallyFrame = innermostFinally(depth);
    if (finallyFrame == null) {
      builder.edge(from, target, FlowEdgeKind.NORMAL);
      return;
    }
    builder.edge(from, finallyFrame.getFinallyEntry(), FlowEdgeKind.NORMAL);
    finallyFrame.addPending(new Continuation(target, depth, false));
  }

  /** Routes an exception from {@code from} to the innermost handler, or to the exit. */
  void propagate(final int from, final FlowEdgeKind kind) {
    for (int index = frames.size() - 1; index >= 0; index--) {
      final Frame frame = frames.get(index);
      if (frame.getKind() == FrameKind.TRY) {
        if (frame.catches()) {
          builder.edge(from, frame.getHandler(), kind);
          return;
        }
        if (frame.runsFinally()) {
          builder.edge(from, frame.getFinallyEntry(), kind);
          frame.addPending(Continuation.RAISE);
          return;
        }
      }
    }
    builder.edge(from, ControlFlowBuilder.EXIT, FlowEdgeKind.EXCEPTION);
  }

  /** Reports whether an exception raised now would reach a handler or a finally block. */
  boolean catchesRaise() {
    for (final Frame frame : frames) {
      if (frame.getKind() == FrameKind.TRY
          && (frame.catches() || frame.runsFinally() || frame.inBody())) {
        return true;
      }
    }
    return false;
  }

  private Frame innermostFinally(final int depth) {
    for (int index = frames.size() - 1; index > depth; index--) {
      final Frame frame = frames.get(index);
      if (frame.runsFinally()) {
        return frame;
      }
    }
    return null;
  }

  /** The kinds of frame a body can be inside of. */
  enum FrameKind {
    FUNCTION,
    LOOP,
    SWITCH,
    LABEL,
    TRY
  }

  /** Where a try statement is: its body, its catch clauses or its finally block. */
  enum Phase {
    BODY,
    CATCH,
    FINALLY
  }

  /**
   * A transfer that waits for a finally block to finish: a jump to {@code target} above the frames
   * at {@code depth}, or, when {@code raise} is set, the propagation of an exception.
   */
  record Continuation(int target, int depth, boolean raise) {

    static final Continuation RAISE = new Continuation(NO_BLOCK, NO_BLOCK, true);
  }

  /** An entry of the frame stack. Only the fields of its kind are meaningful. */
  static final class Frame {

    private final FrameKind kind;
    private final String label;
    private final int breakBlock;
    private final int continueBlock;
    private int handler = NO_BLOCK;
    private int finallyEntry = NO_BLOCK;
    private final List<Integer> ends = new ArrayList<>();
    private final Set<Continuation> pending = new LinkedHashSet<>();
    private Phase phase = Phase.BODY;

    private Frame(
        final FrameKind kind, final String label, final int breakBlock, final int continueBlock) {
      this.kind = kind;
      this.label = label;
      this.breakBlock = breakBlock;
      this.continueBlock = continueBlock;
    }

    static Frame function() {
      return new Frame(FrameKind.FUNCTION, null, NO_BLOCK, NO_BLOCK);
    }

    static Frame target(
        final FrameKind kind, final String label, final int breakBlock, final int continueBlock) {
      return new Frame(kind, label, breakBlock, continueBlock);
    }

    static Frame tryFrame(final int handler, final int finallyEntry) {
      final Frame frame = new Frame(FrameKind.TRY, null, NO_BLOCK, NO_BLOCK);
      frame.handler = handler;
      frame.finallyEntry = finallyEntry;
      return frame;
    }

    FrameKind getKind() {
      return kind;
    }

    String getLabel() {
      return label;
    }

    int getBreakBlock() {
      return breakBlock;
    }

    int getContinueBlock() {
      return continueBlock;
    }

    int getHandler() {
      return handler;
    }

    int getFinallyEntry() {
      return finallyEntry;
    }

    List<Integer> getEnds() {
      return ends;
    }

    Set<Continuation> getPending() {
      return pending;
    }

    void setPhase(final Phase next) {
      phase = next;
    }

    /** A try whose body or catch clauses can hand an exception to its catch clauses. */
    boolean catches() {
      return kind == FrameKind.TRY && phase == Phase.BODY && handler != NO_BLOCK;
    }

    /**
     * A try whose body is running, so an exception raised there leaves it, even with no handler.
     */
    boolean inBody() {
      return kind == FrameKind.TRY && phase == Phase.BODY;
    }

    /** A try whose finally block still runs before control leaves it. */
    boolean runsFinally() {
      return kind == FrameKind.TRY && phase != Phase.FINALLY && finallyEntry != NO_BLOCK;
    }

    boolean accepts(final String target, final boolean continuing) {
      return switch (kind) {
        case LOOP -> target == null || target.equals(label);
        case SWITCH -> !continuing && target == null;
        case LABEL -> !continuing && target != null && target.equals(label);
        case FUNCTION, TRY -> false;
      };
    }

    void addEnd(final int block) {
      ends.add(block);
    }

    void addPending(final Continuation continuation) {
      pending.add(continuation);
    }
  }
}
