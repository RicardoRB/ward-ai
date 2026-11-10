package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The accesses of Java expressions: reads of variables, assignments, increments, and the captures
 * that a nested lambda or class body makes. A simple assignment writes after its right side; a
 * compound one and an increment read first.
 */
final class JavaReferences {

  private static final String ASSIGN = "=";
  private static final int BINARY_PARTS = 3;

  private final JavaScopes scopes;
  private final JavaWalker walker;

  JavaReferences(final JavaScopes scopes, final JavaWalker walker) {
    this.scopes = scopes;
    this.walker = walker;
  }

  /**
   * Adds the accesses of an expression, which is a binary, a postfix or a prefix form, or plain.
   */
  void expression(final SyntaxNode node, final List<VariableAccess> found) {
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(node);
    if (parts.size() == BINARY_PARTS && JavaSyntaxNames.isAssignment(parts.get(1))) {
      assignment(parts, found);
    } else if (parts.size() == 2 && JavaSyntaxNames.isIncrement(parts)) {
      increment(parts, found);
    } else {
      for (final SyntaxNode child : node.children()) {
        walker.walk(child, found);
      }
    }
  }

  /** A simple assignment writes after its right side; a compound one also reads first. */
  void assignment(final List<SyntaxNode> parts, final List<VariableAccess> found) {
    final SyntaxNode target = JavaSyntaxNames.simpleVariable(parts.get(0));
    if (target == null) {
      walker.walk(parts.get(0), found);
      walker.walk(parts.get(2), found);
      return;
    }
    if (!ASSIGN.equals(JavaFlowSyntax.text(parts.get(1)))) {
      read(target, found);
    }
    walker.walk(parts.get(2), found);
    write(target, found);
  }

  /** An increment or decrement reads the variable, then writes it. */
  void increment(final List<SyntaxNode> parts, final List<VariableAccess> found) {
    final SyntaxNode operand =
        JavaSyntaxNames.isOperator(parts.get(0)) ? parts.get(1) : parts.get(0);
    final SyntaxNode target = JavaSyntaxNames.simpleVariable(operand);
    if (target == null) {
      walker.walk(operand, found);
      return;
    }
    read(target, found);
    write(target, found);
  }

  /** A plain variable reference reads the variable it names. */
  void primary(final SyntaxNode node, final List<VariableAccess> found) {
    final SyntaxNode name = JavaSyntaxNames.simpleVariable(node);
    if (name == null) {
      for (final SyntaxNode child : node.children()) {
        walker.walk(child, found);
      }
    } else {
      read(name, found);
    }
  }

  /**
   * A nested lambda or class body reads or writes the outer variables it names. Each variable is
   * captured once, at the place the body is created.
   */
  void captures(final SyntaxNode node, final List<VariableAccess> found) {
    final Set<Span> captured = new LinkedHashSet<>();
    final List<SyntaxNode> names = new ArrayList<>(JavaSyntaxNames.namesIn(node));
    for (final SyntaxNode name : names) {
      final Span declaration = scopes.resolve(JavaFlowSyntax.text(name));
      if (declaration != null && captured.add(declaration)) {
        found.add(
            new VariableAccess(
                JavaFlowSyntax.text(name), AccessKind.CAPTURE, node.span(), declaration));
      }
    }
  }

  private void read(final SyntaxNode name, final List<VariableAccess> found) {
    scopes.access(AccessKind.READ, name, name.span(), found);
  }

  private void write(final SyntaxNode name, final List<VariableAccess> found) {
    scopes.access(AccessKind.WRITE, name, name.span(), found);
  }
}
