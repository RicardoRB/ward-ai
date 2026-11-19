package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Opens scopes for the Rust walk and counts them, so that a construct that opens several (every
 * {@code let} starts a scope of its own) can close them all at once. It also knows how far a
 * {@code let} reaches: to the end of the innermost block or condition body that holds it.
 */
final class RustScopeStack {
  private final ScopeBuilder builder;
  private final Deque<Span> reach = new ArrayDeque<>();
  private int depth;

  RustScopeStack(final ScopeBuilder builder) {
    this.builder = builder;
  }

  /** Opens a scope inside the current one; returns its id. */
  int open(final ScopeKind kind, final Span span) {
    depth++;
    return builder.open(kind, span);
  }

  /** The number of scopes open now, not counting the module. */
  int depth() {
    return depth;
  }

  /** Closes scopes until only {@code target} are open. */
  void closeTo(final int target) {
    while (depth > target) {
      builder.close();
      depth--;
    }
  }

  /** Starts a region that a {@code let} inside it reaches the end of. */
  void pushReach(final Span region) {
    reach.push(region);
  }

  /** Ends the innermost region. */
  void popReach() {
    reach.pop();
  }

  /** Opens a scope that starts where {@code done} ends and reaches the end of the region. */
  void openAfter(final Span done) {
    final Span end = reach.isEmpty() ? done : reach.peek();
    final boolean later = end.endOffset() >= done.endOffset();
    open(
        ScopeKind.BLOCK,
        new Span(
            done.endLine(),
            done.endColumn(),
            later ? end.endLine() : done.endLine(),
            later ? end.endColumn() : done.endColumn(),
            done.endOffset(),
            later ? end.endOffset() : done.endOffset()
        )
    );
  }
}
