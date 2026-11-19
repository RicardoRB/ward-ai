package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.domain.format.Document;
import dev.ward.analysis.frontend.antlr.format.rust.RustFrame.Kind;
import dev.ward.analysis.frontend.antlr.format.rust.RustFrame.Origin;
import java.util.ArrayDeque;
import java.util.Deque;

/** The stack of nesting levels of the document being built. */
final class RustFrames {
  private final Deque<RustFrame> stack = new ArrayDeque<>();

  /** Starts with the frame of the whole file. */
  RustFrames() {
    stack.push(RustFrame.of(Kind.ROOT, RustFrame.NO_CLOSE));
  }

  /**
   * The innermost frame.
   *
   * @return the frame that receives the next part
   */
  RustFrame top() {
    return stack.peek();
  }

  /**
   * Opens a frame.
   *
   * @param frame the frame
   */
  void push(final RustFrame frame) {
    stack.push(frame);
  }

  /** Closes the innermost frame and adds its document to the frame around it. */
  void pop() {
    final RustFrame frame = stack.pop();
    top().add(frame.document());
  }

  /**
   * Closes the continuation frames on top.
   *
   * @param includingWhere true to close the continuation of a {@code where} clause too
   */
  void popContinuations(final boolean includingWhere) {
    while (top().getKind() == Kind.CONTINUATION
        && (includingWhere || top().getOrigin() != Origin.WHERE)) {
      pop();
    }
  }

  /** Closes the continuation of a {@code where} clause when the body begins. */
  void popWhere() {
    while (top().getKind() == Kind.CONTINUATION && top().getOrigin() == Origin.WHERE) {
      pop();
    }
  }

  /**
   * Indents a line that continues the statement: once for the statement, and once more for a
   * method chain after an assignment or a line that starts a binary operand.
   *
   * @param origin what the continuation starts after
   * @param dot whether the new line starts with a method call or field access
   */
  void continueStatement(final Origin origin, final boolean dot) {
    final RustFrame top = top();
    if (top.getKind() == Kind.HUGGING) {
      return;
    }
    if (top.getKind() != Kind.CONTINUATION) {
      push(RustFrame.continuation(origin));
    } else if (top.getOrigin() == Origin.ASSIGNMENT || dot && top.getOrigin() == Origin.OTHER) {
      push(RustFrame.continuation(dot ? Origin.DOT : Origin.OTHER));
    }
  }

  /**
   * Closes every frame and returns the document of the file.
   *
   * @return the document of the root frame
   */
  Document finish() {
    while (stack.size() > 1) {
      pop();
    }
    return top().document();
  }
}
