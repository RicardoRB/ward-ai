package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The accesses of TypeScript and JavaScript expressions: reads of variables, assignments,
 * increments, shorthand properties and the captures that a nested function or class makes. A simple
 * assignment writes after its right side; a compound one and an increment read first.
 */
final class NodeReferences {

  private static final String EQUALS = "=";
  private static final int PAIR = 2;
  private static final int TRIPLE = 3;
  private static final String COLON = ":";
  private static final String DOT = ".";
  private static final String OPTIONAL_DOT = "?.";
  private static final Set<String> INCREMENTS = Set.of("++", "--");
  private static final Set<String> MEMBER_TOKENS = Set.of(DOT, OPTIONAL_DOT);

  private final NodeScopes scopes;
  private final NodeWalker walker;
  private final NodeDeclarations declarations;

  NodeReferences(
      final NodeScopes scopes, final NodeWalker walker, final NodeDeclarations declarations) {
    this.scopes = scopes;
    this.walker = walker;
    this.declarations = declarations;
  }

  /** Adds the accesses of an expression: a declaration header, an assignment, or a plain one. */
  void singleExpression(final SyntaxNode node, final List<VariableAccess> found) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (parts.size() >= PAIR && NodeSyntax.isKeyword(parts.get(0))) {
      declarations.keywordExpression(parts, found);
    } else if (parts.size() == TRIPLE && NodeSyntax.isAssignment(parts.get(1))) {
      assignment(parts, found);
    } else if (parts.size() == PAIR) {
      unary(node, parts, found);
    } else if (parts.size() == TRIPLE
        && MEMBER_TOKENS.contains(NodeFlowSyntax.text(parts.get(1)))) {
      walker.walk(parts.get(0), found);
    } else {
      walkChildren(node, found);
    }
  }

  /** An operand with an increment or decrement reads and writes it; any other pair is plain. */
  private void unary(
      final SyntaxNode node, final List<SyntaxNode> parts, final List<VariableAccess> found) {
    if (INCREMENTS.contains(NodeFlowSyntax.text(parts.get(1)))) {
      increment(parts.get(0), found);
    } else if (INCREMENTS.contains(NodeFlowSyntax.text(parts.get(0)))) {
      increment(parts.get(1), found);
    } else {
      walkChildren(node, found);
    }
  }

  private void walkChildren(final SyntaxNode node, final List<VariableAccess> found) {
    for (final SyntaxNode child : node.children()) {
      walker.walk(child, found);
    }
  }

  /** A property reads the variable it names when it is shorthand; a keyed one reads its value. */
  void propertyAssignment(final SyntaxNode node, final List<VariableAccess> found) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (NodeSyntax.hasToken(parts, COLON)) {
      walker.walk(parts.get(parts.size() - 1), found);
    } else {
      for (final SyntaxNode child : node.children()) {
        walker.walk(child, found);
      }
    }
  }

  /**
   * A nested function or class reads and writes the outer variables it names. Each variable is
   * captured once, where the body is created.
   */
  void captures(final SyntaxNode node, final List<VariableAccess> found) {
    final Set<Span> captured = new LinkedHashSet<>();
    final List<SyntaxNode> names = new ArrayList<>(NodeBindings.namesIn(node));
    for (final SyntaxNode name : names) {
      final Span declaration = scopes.resolve(NodeFlowSyntax.text(name));
      if (declaration != null && captured.add(declaration)) {
        found.add(
            new VariableAccess(
                NodeFlowSyntax.text(name), AccessKind.CAPTURE, node.span(), declaration));
      }
    }
  }

  /** A simple assignment writes after its right side; a compound one also reads first. */
  private void assignment(final List<SyntaxNode> parts, final List<VariableAccess> found) {
    final SyntaxNode target = NodeSyntax.simpleTarget(parts.get(0));
    if (target == null) {
      walker.walk(parts.get(0), found);
      walker.walk(parts.get(2), found);
      return;
    }
    if (!EQUALS.equals(NodeFlowSyntax.text(parts.get(1)))) {
      read(target, found);
    }
    walker.walk(parts.get(2), found);
    write(target, found);
  }

  /** An increment or decrement reads the variable, then writes it. */
  private void increment(final SyntaxNode operand, final List<VariableAccess> found) {
    final SyntaxNode target = NodeSyntax.simpleTarget(operand);
    if (target == null) {
      walker.walk(operand, found);
      return;
    }
    read(target, found);
    write(target, found);
  }

  /** Adds a read of a name, if some declaration of this body declares it. */
  void read(final SyntaxNode name, final List<VariableAccess> found) {
    if (name != null) {
      scopes.access(AccessKind.READ, name, name.span(), found);
    }
  }

  private void write(final SyntaxNode name, final List<VariableAccess> found) {
    if (name != null) {
      scopes.access(AccessKind.WRITE, name, name.span(), found);
    }
  }
}
