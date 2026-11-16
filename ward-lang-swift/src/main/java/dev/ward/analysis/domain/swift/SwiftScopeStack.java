package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Opens and closes the scopes of the Swift walk and remembers which are open, so that the walk can
 * name the scope around the current one.
 */
final class SwiftScopeStack {
  private final ScopeBuilder builder;
  private final Deque<Integer> open = new ArrayDeque<>();

  SwiftScopeStack(final ScopeBuilder builder) {
    this.builder = builder;
    open.push(UnitScopes.MODULE_ID);
  }

  /** Opens a scope inside the current one and makes it current; returns its id. */
  int open(final ScopeKind kind, final Span span) {
    final int id = builder.open(kind, span);
    open.push(id);
    return id;
  }

  /** Makes the enclosing scope current again. */
  void close() {
    builder.close();
    open.pop();
  }

  /** Returns the id of the scope around the current one; the module scope has itself. */
  int enclosing() {
    final int current = open.pop();
    final int around = open.isEmpty() ? current : open.peek();
    open.push(current);
    return around;
  }
}
