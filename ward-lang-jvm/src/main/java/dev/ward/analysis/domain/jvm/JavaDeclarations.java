package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The accesses of Java declarations: local variables, resources, parameters and catch parameters. A
 * declaration with a value is a write at its declaration, as an initializer is.
 */
final class JavaDeclarations {

  private static final String IDENTIFIER = "identifier";
  private static final String VARIABLE_DECLARATOR_ID = "variableDeclaratorId";
  private static final String VARIABLE_INITIALIZER = "variableInitializer";
  private static final String EXPRESSION = "expression";
  private static final String LAMBDA_PARAMETERS = "lambdaParameters";
  private static final Set<String> PARAMETER_HOLDERS =
      Set.of("formalParameters", "formalParameterList", "formalParameter", LAMBDA_PARAMETERS);

  private final JavaScopes scopes;
  private final JavaWalker walker;

  JavaDeclarations(final JavaScopes scopes, final JavaWalker walker) {
    this.scopes = scopes;
    this.walker = walker;
  }

  /** Declares the parameters of a body and returns their entry accesses, in source order. */
  List<VariableAccess> parameters(final SyntaxNode parameters) {
    final List<VariableAccess> found = new ArrayList<>();
    if (parameters != null) {
      collectParameters(parameters, found);
    }
    return found;
  }

  /** Declares a catch parameter; the exception is its initial value. */
  List<VariableAccess> catchParameter(final SyntaxNode name) {
    final List<VariableAccess> found = new ArrayList<>();
    declareWithValue(name, true, found);
    return found;
  }

  /** A declarator reads its initializer, declares the name, and writes it when it has one. */
  void declarator(final SyntaxNode node, final List<VariableAccess> found) {
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(node);
    final SyntaxNode initializer = JavaFlowSyntax.firstOf(parts, VARIABLE_INITIALIZER);
    if (initializer != null) {
      walker.walk(initializer, found);
    }
    declareWithValue(
        JavaSyntaxNames.identifierOf(JavaFlowSyntax.firstOf(parts, VARIABLE_DECLARATOR_ID)),
        initializer != null,
        found);
  }

  /** A resource declares its name and writes the value it is initialized with. */
  void resource(final SyntaxNode node, final List<VariableAccess> found) {
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(node);
    final SyntaxNode name = JavaSyntaxNames.nameOfResource(parts);
    if (name == null) {
      return;
    }
    final SyntaxNode value = JavaFlowSyntax.firstOf(parts, EXPRESSION);
    if (value != null) {
      walker.walk(value, found);
    }
    declareWithValue(name, value != null, found);
  }

  /** Declares a name and, when it has a value, writes it at the declaration. */
  void declareWithValue(
      final SyntaxNode name, final boolean hasValue, final List<VariableAccess> found) {
    if (name == null) {
      return;
    }
    found.add(scopes.declare(name, AccessKind.DECLARE));
    if (hasValue) {
      scopes.access(AccessKind.WRITE, name, name.span(), found);
    }
  }

  private void collectParameters(final SyntaxNode node, final List<VariableAccess> found) {
    for (final SyntaxNode child : JavaFlowSyntax.parts(node)) {
      if (VARIABLE_DECLARATOR_ID.equals(child.kind())) {
        found.add(
            scopes.declare(
                JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(child), IDENTIFIER),
                AccessKind.PARAMETER));
      } else if (IDENTIFIER.equals(child.kind()) && LAMBDA_PARAMETERS.equals(node.kind())) {
        found.add(scopes.declare(child, AccessKind.PARAMETER));
      } else if (PARAMETER_HOLDERS.contains(child.kind())) {
        collectParameters(child, found);
      }
    }
  }
}
