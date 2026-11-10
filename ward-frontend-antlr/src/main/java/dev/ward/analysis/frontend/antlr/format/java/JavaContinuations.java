package dev.ward.analysis.frontend.antlr.format.java;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The continuation lines of the statement being written in one frame. The first line break inside a
 * statement starts a continuation: what follows it, up to the end of the statement, is indented by
 * the continuation levels, so a nested list or body indents from the continued line. A line that
 * starts with a tighter kind of token than the continuation it is in (see {@link
 * JavaBreakRules#lineKind}) goes one continuation deeper, and a looser one returns to the
 * continuation of its kind.
 */
final class JavaContinuations {

  private final List<Document> parts;
  private final Deque<Continuation> open = new ArrayDeque<>();
  private int levels;

  /**
   * Creates the continuations of one frame.
   *
   * @param parts the frame's documents, which this object rewrites in place
   */
  JavaContinuations(final List<Document> parts) {
    this.parts = parts;
  }

  /**
   * Breaks the line inside a statement.
   *
   * @param continuationLevels the indentation levels of a continuation line
   * @param lineKind the line kind of the token that starts the next line
   */
  void continueLine(final int continuationLevels, final int lineKind) {
    while (open.size() > 1 && open.peek().kind() > lineKind) {
      indentFrom(open.pop().start());
    }
    if (open.isEmpty() || lineKind > open.peek().kind()) {
      open.push(new Continuation(parts.size(), lineKind));
    }
    levels = continuationLevels;
    parts.add(Document.hardLine());
  }

  /** Tells whether the statement has broken onto a continuation line. */
  boolean active() {
    return !open.isEmpty();
  }

  /** Ends the open continuations: what each one holds moves to its continuation indentation. */
  void end() {
    while (!open.isEmpty()) {
      indentFrom(open.pop().start());
    }
  }

  private void indentFrom(final int start) {
    final List<Document> tail = parts.subList(start, parts.size());
    final Document moved = JavaFrame.indented(new Document.Concat(new ArrayList<>(tail)), levels);
    tail.clear();
    parts.add(moved);
  }

  /**
   * One open continuation.
   *
   * @param start the index of the first part it holds
   * @param kind the line kind of the token that started it
   */
  private record Continuation(int start, int kind) {}
}
