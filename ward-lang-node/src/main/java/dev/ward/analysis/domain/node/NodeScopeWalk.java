package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.EnumSet;
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
          "propertyMemberDeclaration",
          "propertyAssignment");
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
  private static final Set<ScopeKind> HOIST_TARGETS =
      Set.of(ScopeKind.FUNCTION, ScopeKind.LAMBDA, ScopeKind.MODULE);
  private static final Set<String> DECLARATIONS =
      Set.of("functionDeclaration", "generatorFunctionDeclaration");
  private static final String SINGLE = "singleExpression";
  private static final String IDENTIFIER = "identifier";

  private final ScopeBuilder builder;
  private final List<Span> imports;
  private final NodeScopeNames names;
  private final NodeScopeExpressions expressions;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();
  private boolean exporting;
  private boolean expressionFunction;

  NodeScopeWalk(final ScopeBuilder builder, final List<Span> imports) {
    this.builder = builder;
    this.imports = imports;
    this.names = new NodeScopeNames(builder, this::visit);
    this.expressions = new NodeScopeExpressions(builder, names, this::visit);
    handlers.put(SINGLE, expressions::expression);
    handlers.put("exportAliasName", expressions::exportedName);
    handlers.put("typeName", expressions::typeName);
    handlers.put("typeQuery", expressions::typeQuery);
    handlers.put("block", node -> scoped(node, ScopeKind.BLOCK));
    handlers.put("caseBlock", node -> scoped(node, ScopeKind.BLOCK));
    handlers.put("iterationStatement", this::loop);
    handlers.put("variableDeclarationList", this::variableStatement);
    handlers.put("catchProduction", this::catchClause);
    handlers.put("variableStatement", this::variableStatement);
    handlers.put("singleVariableDeclaration", this::loopDeclaration);
    handlers.put("classDeclaration", this::classDeclaration);
    handlers.put("classExpression", this::classDeclaration);
    handlers.put("interfaceDeclaration", this::typeDeclaration);
    handlers.put("typeAliasDeclaration", this::typeDeclaration);
    handlers.put("enumDeclaration", this::typeDeclaration);
    handlers.put("namespaceDeclaration", this::namespaceDeclaration);
    handlers.put("exportStatement", this::exportStatement);
    handlers.put("exportFromBlock", this::exportFromBlock);
    handlers.put("arrowFunctionParameters", this::arrowParameters);
    handlers.put("withStatement", this::withStatement);
    handlers.put("typeParameters", expressions::declareTypeParameters);
    FUNCTIONS.forEach(kind -> handlers.put(kind, this::functionLike));
    PARAMETERS.forEach(kind -> handlers.put(kind, this::parameter));
    TYPE_POSITIONS.forEach(kind -> handlers.put(kind, expressions::types));
    handlers.put("importStatement", node -> {});
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

  private void generic(final SyntaxNode node) {
    node.children().forEach(this::visit);
  }

  private void scoped(final SyntaxNode node, final ScopeKind kind) {
    builder.open(kind, node.span());
    generic(node);
    builder.close();
  }

  /**
   * A loop has a scope for its header; {@code for (x of y)} without a declaration writes {@code x}.
   */
  private void loop(final SyntaxNode node) {
    builder.open(ScopeKind.BLOCK, node.span());
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    for (int index = 0; index < parts.size(); index++) {
      final String token = NodeFlowSyntax.text(parts.get(index));
      final boolean iterates = "in".equals(token) || "of".equals(token);
      if (iterates && index > 0 && SINGLE.equals(parts.get(index - 1).kind())) {
        names.target(parts.get(index - 1), false);
      } else if (!(index + 1 < parts.size()
          && isIterator(parts.get(index + 1))
          && SINGLE.equals(parts.get(index).kind()))) {
        visit(parts.get(index));
      }
    }
    builder.close();
  }

  private static boolean isIterator(final SyntaxNode part) {
    return "in".equals(NodeFlowSyntax.text(part)) || "of".equals(NodeFlowSyntax.text(part));
  }

  private void withStatement(final SyntaxNode node) {
    builder.markOpen(builder.current());
    generic(node);
  }

  private void catchClause(final SyntaxNode node) {
    builder.open(ScopeKind.BLOCK, node.span());
    for (final SyntaxNode part : NodeFlowSyntax.parts(node)) {
      if ("assignable".equals(part.kind()) || IDENTIFIER.equals(part.kind())) {
        names.declare(part, builder.current(), BindingKind.PARAMETER, Set.of(), false);
      } else {
        visit(part);
      }
    }
    builder.close();
  }

  private void variableStatement(final SyntaxNode node) {
    final SyntaxNode modifier = modifierOf(node);
    if (modifier == null) {
      expressions.undeclared(node);
      return;
    }
    final String keyword = NodeFlowSyntax.text(modifier);
    for (final SyntaxNode declaration : declarations(node)) {
      declareVariable(declaration, keyword, false);
    }
  }

  private void loopDeclaration(final SyntaxNode node) {
    final SyntaxNode modifier = modifierOf(node);
    final String keyword = modifier == null ? "let" : NodeFlowSyntax.text(modifier);
    NodeFlowSyntax.allOf(NodeFlowSyntax.parts(node), "variableDeclaration")
        .forEach(declaration -> declareVariable(declaration, keyword, true));
  }

  /** The {@code varModifier} of a statement or of its declaration list, or null. */
  private static SyntaxNode modifierOf(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    final SyntaxNode own = NodeFlowSyntax.firstOf(parts, "varModifier");
    if (own != null) {
      return own;
    }
    final SyntaxNode list = NodeFlowSyntax.firstOf(parts, "variableDeclarationList");
    return list == null ? null : NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(list), "varModifier");
  }

  private static List<SyntaxNode> declarations(final SyntaxNode statement) {
    final SyntaxNode list =
        "variableDeclarationList".equals(statement.kind())
            ? statement
            : NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(statement), "variableDeclarationList");
    return list == null
        ? List.of()
        : NodeFlowSyntax.allOf(NodeFlowSyntax.parts(list), "variableDeclaration");
  }

  /** Declares the target of one declarator under the keyword's rules, then visits the rest. */
  private void declareVariable(
      final SyntaxNode declaration, final String keyword, final boolean loop) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(declaration);
    final boolean isVar = "var".equals(keyword);
    final int scope = isVar ? builder.nearest(HOIST_TARGETS) : builder.current();
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    flags.add(isVar ? BindingFlag.HOISTED : BindingFlag.LEXICAL);
    if ("const".equals(keyword)) {
      flags.add(BindingFlag.CONST);
    }
    if (exporting && scope == UnitScopes.MODULE_ID) {
      flags.add(BindingFlag.EXPORTED);
    }
    parts.stream().skip(1).forEach(this::visit);
    names.declare(
        parts.get(0),
        scope,
        "const".equals(keyword) ? BindingKind.CONSTANT : BindingKind.VARIABLE,
        flags,
        loop);
  }

  private void functionLike(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (!hasBody(parts)) {
      expressionFunction = "anonymousFunction".equals(node.kind());
      generic(node);
      expressionFunction = false;
      return;
    }
    final boolean declaration = DECLARATIONS.contains(node.kind()) && !expressionFunction;
    expressionFunction = false;
    final SyntaxNode name = NodeFlowSyntax.firstOf(parts, IDENTIFIER);
    if (declaration && name != null) {
      declareFunctionName(name);
    }
    builder.open(isArrow(node, parts) ? ScopeKind.LAMBDA : ScopeKind.FUNCTION, node.span());
    if (!declaration && name != null && DECLARATIONS.contains(node.kind())) {
      builder.declare(NodeFlowSyntax.text(name), BindingKind.FUNCTION, name.span(), Set.of());
    }
    generic(node);
    builder.close();
  }

  private static boolean hasBody(final List<SyntaxNode> parts) {
    return NodeFlowSyntax.firstOf(parts, "functionBody") != null
        || NodeFlowSyntax.firstOf(parts, "arrowFunctionBody") != null;
  }

  private static boolean isArrow(final SyntaxNode node, final List<SyntaxNode> parts) {
    return "arrowFunctionDeclaration".equals(node.kind())
        || NodeFlowSyntax.firstOf(parts, "arrowFunctionParameters") != null;
  }

  private void declareFunctionName(final SyntaxNode name) {
    final Set<BindingFlag> flags = EnumSet.of(BindingFlag.HOISTED);
    if (exporting && builder.current() == UnitScopes.MODULE_ID) {
      flags.add(BindingFlag.EXPORTED);
    }
    final String text = NodeFlowSyntax.text(name);
    builder.declare(text, BindingKind.FUNCTION, name.span(), flags);
  }

  private void parameter(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    SyntaxNode pattern = null;
    for (final SyntaxNode part : parts) {
      if (pattern == null && isPatternHolder(part)) {
        pattern = part;
        names.declare(part, builder.current(), BindingKind.PARAMETER, Set.of(), false);
      } else {
        visit(part);
      }
    }
  }

  private static boolean isPatternHolder(final SyntaxNode part) {
    return Set.of(
            "assignable",
            "identifierOrPattern",
            IDENTIFIER,
            "identifierName",
            SINGLE,
            "arrayLiteral",
            "objectLiteral",
            "bindingPattern")
        .contains(part.kind());
  }

  private void arrowParameters(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (NodeFlowSyntax.firstOf(parts, "formalParameterList") != null) {
      generic(node);
      return;
    }
    parts.stream()
        .filter(part -> !NodeFlowSyntax.text(part).equals("=>"))
        .forEach(
            part -> names.declare(part, builder.current(), BindingKind.PARAMETER, Set.of(), false));
  }

  private void classDeclaration(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    final SyntaxNode name = NodeFlowSyntax.firstOf(parts, IDENTIFIER);
    final Set<BindingFlag> flags = EnumSet.of(BindingFlag.LEXICAL);
    if (exporting && builder.current() == UnitScopes.MODULE_ID) {
      flags.add(BindingFlag.EXPORTED);
    }
    if (name != null && "classDeclaration".equals(node.kind())) {
      builder.declare(NodeFlowSyntax.text(name), BindingKind.CLASS, name.span(), flags);
    }
    builder.open(ScopeKind.CLASS, node.span());
    parts.stream().filter(part -> part != name).forEach(this::visit);
    builder.close();
  }

  private void typeDeclaration(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    final SyntaxNode name = NodeFlowSyntax.firstOf(parts, IDENTIFIER);
    final boolean typeOnly = !"enumDeclaration".equals(node.kind());
    final Set<BindingFlag> flags =
        typeOnly ? EnumSet.of(BindingFlag.TYPE_ONLY) : EnumSet.noneOf(BindingFlag.class);
    if (exporting && builder.current() == UnitScopes.MODULE_ID) {
      flags.add(BindingFlag.EXPORTED);
    }
    if (name != null) {
      builder.declare(NodeFlowSyntax.text(name), BindingKind.TYPE, name.span(), flags);
    }
    builder.open(ScopeKind.BLOCK, node.span());
    expressions.typeParameters(parts);
    parts.stream()
        .filter(part -> part != name && !"typeParameters".equals(part.kind()))
        .forEach(this::visit);
    builder.close();
  }

  private void namespaceDeclaration(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    final SyntaxNode name = NodeFlowSyntax.firstOf(parts, "namespaceName");
    if (name != null && NodeScopeNames.nameOf(name) != null) {
      final SyntaxNode leaf = NodeScopeNames.nameOf(name);
      builder.declare(NodeFlowSyntax.text(leaf), BindingKind.TYPE, leaf.span(), Set.of());
    }
    builder.open(ScopeKind.BLOCK, node.span());
    parts.stream().filter(part -> part != name).forEach(this::visit);
    builder.close();
  }

  private void exportStatement(final SyntaxNode node) {
    final boolean outer = exporting;
    exporting = true;
    generic(node);
    exporting = outer;
  }

  private void exportFromBlock(final SyntaxNode node) {
    final boolean reexport =
        NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(node), "importFrom") != null
            || containsFrom(node);
    if (!reexport) {
      generic(node);
    }
  }

  private static boolean containsFrom(final SyntaxNode node) {
    return NodeFlowSyntax.allOf(NodeFlowSyntax.parts(node), "importFrom").size() > 0
        || NodeFlowSyntax.parts(node).stream().anyMatch(part -> "importFrom".equals(part.kind()));
  }
}
