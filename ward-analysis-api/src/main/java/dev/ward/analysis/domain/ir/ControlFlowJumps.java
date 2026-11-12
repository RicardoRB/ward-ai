package dev.ward.analysis.domain.ir;

import dev.ward.analysis.domain.ir.ControlFlowFrames.Frame;
import dev.ward.analysis.domain.ir.ControlFlowFrames.FrameKind;

/**
 * Opens and closes the loops, switches and labels of a body, and lowers {@code break}, {@code
 * continue} and {@code yield} to jumps to their targets.
 */
public final class ControlFlowJumps {

  private final ControlFlowFrames frames;

  ControlFlowJumps(final ControlFlowFrames frames) {
    this.frames = frames;
  }

  /**
   * Opens a loop. Its break target is the block after the loop and its continue target is the block
   * that runs the next iteration.
   *
   * @param label the label written on the loop, or null
   * @param breakBlock the block a {@code break} reaches
   * @param continueBlock the block a {@code continue} reaches
   */
  public void pushLoop(final String label, final int breakBlock, final int continueBlock) {
    frames.push(Frame.target(FrameKind.LOOP, label, breakBlock, continueBlock));
  }

  /**
   * Opens a switch, which takes unlabelled {@code break} and {@code yield}.
   *
   * @param breakBlock the block after the switch
   */
  public void pushSwitch(final int breakBlock) {
    frames.push(Frame.target(FrameKind.SWITCH, null, breakBlock, ControlFlowFrames.NO_BLOCK));
  }

  /**
   * Opens a labelled statement that is not a loop, so that {@code break label} can leave it.
   *
   * @param label the label
   * @param breakBlock the block after the statement
   */
  public void pushLabel(final String label, final int breakBlock) {
    frames.push(Frame.target(FrameKind.LABEL, label, breakBlock, ControlFlowFrames.NO_BLOCK));
  }

  /** Closes the innermost loop, switch or label. */
  public void popFrame() {
    frames.pop();
  }

  /**
   * Jumps to the loop or label with the label, or to the innermost loop or switch when the label is
   * null, and continues from a fresh block.
   *
   * @param label the label, or null
   * @throws IllegalStateException if no enclosing loop or switch matches
   */
  public void breakTo(final String label) {
    final int depth = findTarget(label, false);
    frames.transfer(frames.at(depth).getBreakBlock(), depth);
  }

  /**
   * Jumps to the next iteration of the loop with the label, or of the innermost loop when the label
   * is null, and continues from a fresh block.
   *
   * @param label the label, or null
   * @throws IllegalStateException if no enclosing loop matches
   */
  public void continueTo(final String label) {
    final int depth = findTarget(label, true);
    frames.transfer(frames.at(depth).getContinueBlock(), depth);
  }

  /**
   * Leaves the innermost switch, which is how a {@code yield} ends a switch expression, and
   * continues from a fresh block.
   *
   * @throws IllegalStateException if no enclosing switch exists
   */
  public void yieldTo() {
    for (int depth = frames.size() - 1; depth > 0; depth--) {
      if (frames.at(depth).getKind() == FrameKind.SWITCH) {
        frames.transfer(frames.at(depth).getBreakBlock(), depth);
        return;
      }
    }
    throw new IllegalStateException("yield outside a switch expression");
  }

  private int findTarget(final String label, final boolean continuing) {
    for (int depth = frames.size() - 1; depth > 0; depth--) {
      if (frames.at(depth).accepts(label, continuing)) {
        return depth;
      }
    }
    throw new IllegalStateException("no enclosing target for " + label);
  }
}
