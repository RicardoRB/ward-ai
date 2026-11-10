package dev.ward.analysis.frontend.antlr.format.java;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayDeque;
import java.util.Deque;

/** The open bracket scopes of one layout, innermost first, with the switch-label handling. */
final class JavaFrameStack {

  private final JavaFrame root = new JavaFrame(JavaFrame.Kind.ROOT, "");
  private final Deque<JavaFrame> frames = new ArrayDeque<>();

  JavaFrameStack() {
    frames.push(root);
  }

  JavaFrame root() {
    return root;
  }

  JavaFrame current() {
    return frames.peek();
  }

  boolean blockTop() {
    return JavaBreakRules.isBlockLike(current().kind());
  }

  boolean isRootOnly() {
    return frames.size() == 1;
  }

  void push(final JavaFrame.Kind kind, final String close) {
    frames.push(new JavaFrame(kind, close));
  }

  JavaFrame pop() {
    return frames.pop();
  }

  /** Ends the statements of the previous switch label when a new label starts. */
  void endCaseLabels(final String text) {
    if (JavaBreakRules.CASE.equals(text) || JavaBreakRules.DEFAULT.equals(text)) {
      endCases();
    }
  }

  /** Ends every open switch-label scope, so the next closing bracket finds its own scope. */
  void endCases() {
    while (current().kind() == JavaFrame.Kind.CASE) {
      popCase();
    }
  }

  /** Pops a switch-label scope and writes its statements, indented, into the enclosing block. */
  void popCase() {
    final JavaFrame frame = frames.pop();
    if (!frame.isEmpty()) {
      current().add(Document.indent(frame.body()));
    }
  }
}
