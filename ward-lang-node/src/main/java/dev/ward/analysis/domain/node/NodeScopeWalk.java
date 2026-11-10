package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Walks a TypeScript or JavaScript syntax tree and records its scopes. {@code var} and function
 * declarations hoist to the enclosing function; {@code let}, {@code const} and classes belong to
 * their block; a function, an arrow function and a {@code catch} clause open scopes of their own.
 * Names appear only where the grammar puts an expression, a type reference or a declaration, so a
 * property key, a member name and a label are never references.
 */
final class NodeScopeWalk {

  private static final Set<String> FUNCTIONS =
      Set.of(
          "functionDeclaration",
          "generatorFunctionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "methodDefinition",
          "constructorDeclaration",
          "getAccessor",
          "setAccessor",
          "propertyMemberDeclaration");
  private static final Set<String> PARAMETERS =
      Set.of(
          "formalParameterArg",
          "lastFormalParameterArg",
          "requiredParameter",
          "optionalParameter",
          "restParameter");
  private static final Set<String> TYPE_POSITIONS =
      Set.of(
          "typeAnnotation",
          "typeArguments",
          "typeGeneric",
          "typeReference",
          "implementsClause",
          "interfaceExtendsClause",
          "objectType",
          "type_");
  private static final String SINGLE = "singleExpression";

  final ScopeBuilder builder;
  private final List<Span> imports;
  final NodeScopeNames names;
  final NodeScopeExpressions expressions;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();
  boolean exporting;
  boolean expressionFunction;
  private final NodeScopeStatements statements;
  private final NodeScopeFunctions functions;
  private final NodeScopeTypes types;

  NodeScopeWalk(final ScopeBuilder builder, final List<Span> imports) {
    this.statements = new NodeScopeStatements(this);
    this.functions = new NodeScopeFunctions(this);
    this.types = new NodeScopeTypes(this);
    this.builder = builder;
    this.imports = imports;
    this.names = new NodeScopeNames(builder, this::visit);
    this.expressions = new NodeScopeExpressions(builder, names, this::visit);
    handlers.put(SINGLE, expressions::expression);
    handlers.put("exportAliasName", expressions::exportedName);
    handlers.put("typeName", expressions.typeRefs::typeName);
    handlers.put("typeQuery", expressions.typeRefs::typeQuery);
    handlers.put("predefinedType", expressions.typeRefs::predefinedType);
    handlers.put("jsxElementName", new NodeScopeJsx(expressions)::jsxElementName);
    handlers.put("block", node -> scoped(node, ScopeKind.BLOCK));
    handlers.put("caseBlock", node -> scoped(node, ScopeKind.BLOCK));
    handlers.put("iterationStatement", statements::loop);
    handlers.put("variableDeclarationList", statements::variableStatement);
    handlers.put("catchProduction", statements::catchClause);
    handlers.put("variableStatement", statements::variableStatement);
    handlers.put("singleVariableDeclaration", statements::loopDeclaration);
    handlers.put("forVariableDeclarationList", statements::loopDeclaration);
    handlers.put("classDeclaration", types::classDeclaration);
    handlers.put("classExpression", types::classDeclaration);
    handlers.put("interfaceDeclaration", types::typeDeclaration);
    handlers.put("typeAliasDeclaration", types::typeDeclaration);
    handlers.put("enumDeclaration", types::typeDeclaration);
    handlers.put("namespaceDeclaration", types::namespaceDeclaration);
    handlers.put("exportStatement", types::exportStatement);
    handlers.put("exportFromBlock", types::exportFromBlock);
    handlers.put("arrowFunctionParameters", functions::arrowParameters);
    handlers.put("withStatement", statements::withStatement);
    handlers.put("typeParameters", expressions.typeRefs::declareTypeParameters);
    FUNCTIONS.forEach(kind -> handlers.put(kind, functions::functionLike));
    PARAMETERS.forEach(kind -> handlers.put(kind, functions::parameter));
    TYPE_POSITIONS.forEach(kind -> handlers.put(kind, expressions.typeRefs::types));
    handlers.put("importStatement", node -> {});
    handlers.put("propertyAssignment", this::propertyAssignment);
  }

  /**
   * A shorthand property {@code { id }} reads the variable it names, as TypeScript's grammar keeps
   * it as a bare identifier; any other property is a method, or a keyed value that is visited.
   */
  private void propertyAssignment(final SyntaxNode node) {
    final SyntaxNode name =
        NodeScopeNames.isShorthand(node)
            ? NodeScopeNames.nameOf(NodeFlowSyntax.parts(node).get(0))
            : null;
    if (name == null) {
      functions.functionLike(node);
    } else {
      expressions.read(name);
    }
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    if (imports.stream().anyMatch(site -> within(site, node.span()))) {
      return;
    }
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  private static boolean within(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }

  void generic(final SyntaxNode node) {
    node.children().forEach(this::visit);
  }

  private void scoped(final SyntaxNode node, final ScopeKind kind) {
    builder.open(kind, node.span());
    generic(node);
    builder.close();
  }
}
