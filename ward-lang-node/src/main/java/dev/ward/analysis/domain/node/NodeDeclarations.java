package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;

/**
 * The accesses of TypeScript and JavaScript declarations: variable statements, the declarations in
 * a loop header, loop and catch bindings, parameters and function names. A declaration with a value
 * writes it at the declaration, as an initializer is.
 */
final class NodeDeclarations {

  private static final String VARIABLE_LIST = "variableDeclarationList";
  private static final String VARIABLE_DECLARATION = "variableDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String FUNCTION_DECLARATION = "functionDeclaration";
  private static final String EQUALS = "=";

  private final NodeScopes scopes;
  private final NodeWalker walker;

  NodeDeclarations(final NodeScopes scopes, final NodeWalker walker) {
    this.scopes = scopes;
    this.walker = walker;
  }

  /** A variable statement declares its names when it has a modifier, on it or on its list. */
  void variableStatement(final SyntaxNode node, final List<VariableAccess> found) {
    final SyntaxNode list = NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(node), VARIABLE_LIST);
    if (list == null) {
      walkChildren(node, found);
      return;
    }
    declarationList(list, NodeSyntax.modifies(node) || NodeSyntax.modifies(list), found);
  }

  /** Adds the declarators of a list, each declaring its names when the list has a modifier. */
  void declarationList(
      final SyntaxNode list, final boolean declares, final List<VariableAccess> found) {
    for (final SyntaxNode declaration :
        NodeFlowSyntax.allOf(NodeFlowSyntax.parts(list), VARIABLE_DECLARATION)) {
      declarator(declaration, declares, found);
    }
  }

  /**
   * {@code let x = e} or {@code const x} inside a for header: a declaration of the bound names. The
   * header of a loop declares them without a value; the loop writes them itself.
   */
  void keywordExpression(final List<SyntaxNode> parts, final List<VariableAccess> found) {
    final SyntaxNode binding = parts.get(1);
    final List<SyntaxNode> bound = NodeFlowSyntax.parts(binding);
    if (bound.size() == 3 && NodeSyntax.isAssignment(bound.get(1))) {
      walker.walk(bound.get(2), found);
      for (final SyntaxNode name : NodeBindings.bindings(bound.get(0))) {
        found.add(scopes.declare(name, AccessKind.DECLARE));
        scopes.access(AccessKind.WRITE, name, name.span(), found);
      }
      return;
    }
    for (final SyntaxNode name : NodeBindings.bindings(binding)) {
      found.add(scopes.declare(name, AccessKind.DECLARE));
    }
  }

  /**
   * Writes the names a for-in or for-of binding declares. The loop assigns them on every iteration,
   * so each is a write at its declaration.
   *
   * @param binding the binding node of the loop header
   * @return one write per bound name
   */
  List<VariableAccess> loopBinding(final SyntaxNode binding) {
    final List<VariableAccess> found = new ArrayList<>();
    for (final SyntaxNode name : NodeBindings.bindings(binding)) {
      scopes.access(AccessKind.WRITE, name, name.span(), found);
    }
    return found;
  }

  /**
   * Declares the names of a catch parameter and writes them: the exception is their initial value.
   * A catch without a binding declares nothing.
   *
   * @param parameter the identifier or pattern of the catch parameter, or null
   * @return the declarations and the writes
   */
  List<VariableAccess> catchParameter(final SyntaxNode parameter) {
    final List<VariableAccess> found = new ArrayList<>();
    if (parameter != null) {
      for (final SyntaxNode name : NodeBindings.bindings(parameter)) {
        found.add(scopes.declare(name, AccessKind.DECLARE));
        scopes.access(AccessKind.WRITE, name, name.span(), found);
      }
    }
    return found;
  }

  /** Declares the parameters of a function and returns their entry accesses, in source order. */
  List<VariableAccess> parameters(final SyntaxNode function) {
    final List<VariableAccess> found = new ArrayList<>();
    final SyntaxNode list = function == null ? null : NodeBindings.parameterList(function);
    if (list != null) {
      for (final SyntaxNode leaf : NodeBindings.bindings(list)) {
        found.add(scopes.declare(leaf, AccessKind.PARAMETER));
      }
    }
    return found;
  }

  /**
   * Declares the name of a function declaration. The declaration is hoisted, so it is in scope for
   * the statements around it, but it is not an access of its own.
   */
  void declareFunctionName(final SyntaxNode node) {
    if (FUNCTION_DECLARATION.equals(node.kind())) {
      final SyntaxNode name = NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(node), IDENTIFIER);
      if (name != null) {
        scopes.declare(NodeSyntax.leafOf(name), AccessKind.DECLARE);
      }
    }
  }

  /**
   * A declarator reads its initializer, then declares and writes its names. Without a modifier and
   * without {@code =}, the grammar parses a call such as {@code work(item)} as a declarator, so its
   * parts are read as expressions.
   */
  private void declarator(
      final SyntaxNode declaration, final boolean declares, final List<VariableAccess> found) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(declaration);
    final boolean assigns = NodeSyntax.hasToken(parts, EQUALS);
    if (!declares && !assigns) {
      walkChildren(declaration, found);
      return;
    }
    if (assigns) {
      walker.walk(parts.get(parts.size() - 1), found);
    }
    for (final SyntaxNode name : NodeBindings.bindings(parts.get(0))) {
      if (declares) {
        found.add(scopes.declare(name, AccessKind.DECLARE));
      }
      if (assigns) {
        scopes.access(AccessKind.WRITE, name, name.span(), found);
      }
    }
  }

  private void walkChildren(final SyntaxNode node, final List<VariableAccess> found) {
    for (final SyntaxNode child : node.children()) {
      walker.walk(child, found);
    }
  }
}
