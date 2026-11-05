package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The block scopes of one Java body, each mapping the names it declares to their declarations. */
final class JavaScopes {

  private final Deque<Map<String, Span>> scopes = new ArrayDeque<>();

  /** Creates the scope of the body's parameters and outermost locals. */
  JavaScopes() {
    scopes.push(new HashMap<>());
  }

  /** Opens a block scope; the names declared in it end with {@link #exitScope()}. */
  void enterScope() {
    scopes.push(new HashMap<>());
  }

  /** Closes the innermost block scope. */
  void exitScope() {
    scopes.pop();
  }

  /** Declares a name in the innermost scope and returns the access that declares it. */
  VariableAccess declare(final SyntaxNode name, final AccessKind kind) {
    final String text = JavaFlowSyntax.text(name);
    scopes.peek().put(text, name.span());
    return new VariableAccess(text, kind, name.span(), name.span());
  }

  /** Returns the declaration of a name, or null when no local of this body declares it. */
  Span resolve(final String name) {
    for (final Map<String, Span> scope : scopes) {
      final Span declaration = scope.get(name);
      if (declaration != null) {
        return declaration;
      }
    }
    return null;
  }

  /** Records an access of a name that some declaration of this body declares. */
  void access(
      final AccessKind kind,
      final SyntaxNode name,
      final Span span,
      final List<VariableAccess> found) {
    final String text = JavaFlowSyntax.text(name);
    final Span declaration = resolve(text);
    if (declaration != null) {
      found.add(new VariableAccess(text, kind, span, declaration));
    }
  }
}
