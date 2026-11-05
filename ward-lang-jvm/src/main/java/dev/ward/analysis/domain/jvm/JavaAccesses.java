package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.ir.VariableAccesses;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Finds the accesses of the local variables and parameters of one Java body. A name resolves to the
 * innermost declaration in scope, so the accesses of a variable are tied to its declaration. A name
 * that resolves to no declaration (a field, a type, a name of another body) is not recorded.
 */
final class JavaAccesses implements VariableAccesses {

  private static final String VARIABLE_DECLARATOR = "variableDeclarator";
  private static final String VARIABLE_DECLARATOR_ID = "variableDeclaratorId";
  private static final String RESOURCE = "resource";
  private static final String EXPRESSION = "expression";
  private static final String PRIMARY = "primary";
  private static final String LAMBDA = "lambdaExpression";
  private static final String CLASS_BODY = "classBody";

  private final JavaScopes scopes = new JavaScopes();
  private final JavaDeclarations declarations = new JavaDeclarations(scopes, this::walk);
  private final JavaReferences references = new JavaReferences(scopes, this::walk);
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
   * Declares a catch parameter and returns its write: the exception is its initial value, so it is
   * a write at the declaration, as an initializer is.
   *
   * @param name the identifier of the catch parameter
   * @return the declaration and the write
   */
  List<VariableAccess> catchParameter(final SyntaxNode name) {
    return declarations.catchParameter(name);
  }

  /**
   * Declares the parameters of a body in the outermost scope and returns their entry accesses.
   *
   * @param parameters the formal or lambda parameters, or null for a body without any
   * @return one parameter access per parameter, in source order
   */
  List<VariableAccess> parameters(final SyntaxNode parameters) {
    return declarations.parameters(parameters);
  }

  @Override
  public List<VariableAccess> of(final SyntaxNode node) {
    final List<VariableAccess> found = new ArrayList<>();
    walk(node, found);
    return found;
  }

  private void walk(final SyntaxNode node, final List<VariableAccess> found) {
    if (!skipped.contains(node)) {
      dispatch(node, found);
    }
  }

  private void dispatch(final SyntaxNode node, final List<VariableAccess> found) {
    switch (node.kind()) {
      case VARIABLE_DECLARATOR -> declarations.declarator(node, found);
      // An enhanced-for variable takes its value from the iterable on each iteration, so it is
      // a write at its declaration, as an initializer is.
      case VARIABLE_DECLARATOR_ID ->
          declarations.declareWithValue(JavaSyntaxNames.identifierOf(node), true, found);
      case RESOURCE -> declarations.resource(node, found);
      case EXPRESSION -> references.expression(node, found);
      case PRIMARY -> references.primary(node, found);
      case LAMBDA, CLASS_BODY -> references.captures(node, found);
      default -> node.children().forEach(child -> walk(child, found));
    }
  }
}
