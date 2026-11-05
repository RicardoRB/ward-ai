package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.ir.VariableAccesses;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Finds the accesses of the local variables and parameters of one TypeScript or JavaScript body. A
 * name resolves to the innermost declaration in scope; a name that resolves to none (a global, a
 * property, a name of another body) is not recorded.
 */
final class NodeAccesses implements VariableAccesses {

  private static final String VARIABLE_STATEMENT = "variableStatement";
  private static final String VARIABLE_LIST = "variableDeclarationList";
  private static final String SINGLE_VARIABLE = "singleVariableDeclaration";
  private static final String PROPERTY_ASSIGNMENT = "propertyAssignment";
  private static final String SINGLE = "singleExpression";
  private static final String PROPERTY_NAME = "propertyName";
  private static final String LABELLED = "labelledStatement";
  private static final String BREAK = "breakStatement";
  private static final String CONTINUE = "continueStatement";

  /** Property keys and labels, which name no variable. */
  private static final Set<String> NAMELESS = Set.of(PROPERTY_NAME, LABELLED, BREAK, CONTINUE);

  /** Nodes that bind a name of their own, so the body is walked for captures of outer names. */
  private static final Set<String> CAPTURERS =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "methodDefinition",
          "propertyMemberDeclaration",
          "classDeclaration",
          "classExpression");

  private final NodeScopes scopes = new NodeScopes();
  private final NodeDeclarations declarations = new NodeDeclarations(scopes, this::walk);
  private final NodeReferences references = new NodeReferences(scopes, this::walk, declarations);
  private final Set<SyntaxNode> skipped = Collections.newSetFromMap(new IdentityHashMap<>());

  /** Opens a block scope; the names declared in it end with {@link #exitScope()}. */
  void enterScope() {
    scopes.enterScope();
  }

  /** Closes the innermost block scope. */
  void exitScope() {
    scopes.exitScope();
  }

  /**
   * Excludes a node from the accesses of the statement that contains it, because the lowering
   * already added it as a node of its own block.
   *
   * @param node the node that was lowered separately
   */
  void skip(final SyntaxNode node) {
    skipped.add(node);
  }

  /**
   * Declares the parameters of a function in the outermost scope and returns their entry accesses.
   *
   * @param function the function whose parameters are declared, or null for a body without any
   * @return one parameter access per parameter, in source order
   */
  List<VariableAccess> parameters(final SyntaxNode function) {
    return declarations.parameters(function);
  }

  /**
   * Writes the names a for-in or for-of binding declares. The loop assigns them on every iteration.
   *
   * @param binding the binding node of the loop header
   * @return one write per bound name
   */
  List<VariableAccess> loopBinding(final SyntaxNode binding) {
    return declarations.loopBinding(binding);
  }

  /**
   * Declares the names of a catch parameter and writes them: the exception is their initial value.
   *
   * @param parameter the identifier or pattern of the catch parameter, or null
   * @return the declarations and the writes
   */
  List<VariableAccess> catchParameter(final SyntaxNode parameter) {
    return declarations.catchParameter(parameter);
  }

  @Override
  public List<VariableAccess> of(final SyntaxNode node) {
    final List<VariableAccess> found = new ArrayList<>();
    walk(node, found);
    return found;
  }

  private void walk(final SyntaxNode node, final List<VariableAccess> found) {
    if (skipped.contains(node) || NodeSyntax.SKIPPED.contains(node.kind())) {
      return;
    }
    if (NAMELESS.contains(node.kind())) {
      return;
    }
    switch (node.kind()) {
      case VARIABLE_STATEMENT -> declarations.variableStatement(node, found);
      case VARIABLE_LIST, SINGLE_VARIABLE ->
          declarations.declarationList(node, NodeSyntax.modifies(node), found);
      case SINGLE -> references.singleExpression(node, found);
      case PROPERTY_ASSIGNMENT -> references.propertyAssignment(node, found);
      default -> otherNode(node, found);
    }
  }

  private void otherNode(final SyntaxNode node, final List<VariableAccess> found) {
    if (NodeSyntax.isName(node.kind())) {
      references.read(NodeSyntax.leafOf(node), found);
    } else if (CAPTURERS.contains(node.kind())) {
      references.captures(node, found);
      declarations.declareFunctionName(node);
    } else {
      node.children().forEach(child -> walk(child, found));
    }
  }
}
