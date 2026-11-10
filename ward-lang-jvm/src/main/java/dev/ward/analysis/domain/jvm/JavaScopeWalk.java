package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Walks a Java syntax tree and records its scopes: members in class scopes, parameters in method
 * and lambda scopes, locals in block scopes. Types are not references. A class that extends or
 * implements something, an enum, a record, an interface and an anonymous class are open, because
 * they inherit names the file does not spell, and so is a file with a static wildcard import.
 */
final class JavaScopeWalk {

  private static final Set<String> TYPE_DECLARATIONS =
      Set.of("classDeclaration", "interfaceDeclaration", "enumDeclaration", "recordDeclaration");
  private static final Set<String> METHODS =
      Set.of(
          "methodDeclaration",
          "interfaceCommonBodyDeclaration",
          "constructorDeclaration",
          "compactConstructorDeclaration");
  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "expression";
  private static final String PRIMARY = "primary";

  final ScopeBuilder builder;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();
  boolean finalMember;
  boolean finalLocal;
  final JavaScopeTypes types;
  final JavaScopeBodies bodies;
  final JavaScopeExpressions expressions;

  JavaScopeWalk(final ScopeBuilder builder) {
    this.types = new JavaScopeTypes(this);
    this.bodies = new JavaScopeBodies(this);
    this.expressions = new JavaScopeExpressions(this);
    this.builder = builder;
    handlers.put("importDeclaration", types::importDeclaration);
    handlers.put("annotation", node -> {});
    handlers.put("classBodyDeclaration", types::member);
    handlers.put("interfaceBodyDeclaration", types::member);
    handlers.put("variableDeclarator", types::declarator);
    handlers.put("constantDeclarator", types::constant);
    handlers.put("localVariableDeclaration", types::localVariable);
    handlers.put("recordComponent", types::recordComponent);
    handlers.put("enumConstant", bodies::enumConstant);
    handlers.put("classCreatorRest", bodies::anonymousClass);
    handlers.put("formalParameter", bodies::parameter);
    handlers.put("block", node -> scoped(node, ScopeKind.BLOCK));
    handlers.put("statement", bodies::statement);
    handlers.put("enhancedForControl", bodies::enhancedFor);
    handlers.put("catchClause", bodies::catchClause);
    handlers.put("resource", bodies::resource);
    handlers.put("lambdaExpression", bodies::lambda);
    handlers.put(EXPRESSION, expressions::expression);
    handlers.put(PRIMARY, expressions::primary);
    handlers.put("methodCall", expressions::methodCall);
    handlers.put("switchLabeledRule", expressions::switchRule);
    handlers.put("switchLabel", expressions::switchRule);
    TYPE_DECLARATIONS.forEach(kind -> handlers.put(kind, types::typeDeclaration));
    METHODS.forEach(kind -> handlers.put(kind, types::method));
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  void generic(final SyntaxNode node) {
    node.children().forEach(this::visit);
  }

  void scoped(final SyntaxNode node, final ScopeKind kind) {
    builder.open(kind, node.span());
    generic(node);
    builder.close();
  }

  static String text(final SyntaxNode node) {
    return JavaSyntax.dotted(node);
  }

  static SyntaxNode identifierOf(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .orElse(null);
  }
}
