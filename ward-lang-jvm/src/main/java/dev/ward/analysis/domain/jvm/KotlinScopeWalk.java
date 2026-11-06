package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Walks a Kotlin syntax tree and records its scopes: members in class scopes, parameters in
 * function and lambda scopes, locals in block scopes. Types are not references. A class with
 * supertypes, an interface, an enum, an object literal, an extension function and a lambda with no
 * parameters are open, because they bring in names the file does not spell, and so is a class that
 * has a companion object.
 */
final class KotlinScopeWalk {

  private static final Set<String> SKIPPED =
      Set.of(
          "type",
          "userType",
          "typeParameters",
          "typeArguments",
          "typeConstraints",
          "annotations",
          "annotation",
          "modifierList",
          "receiverType",
          "labelDefinition",
          "typeTest");
  private static final String SIMPLE = "simpleIdentifier";
  private static final String BLOCK = "block";
  private static final String EXPRESSION = "expression";
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String ATOMIC = "atomicExpression";

  final ScopeBuilder builder;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();
  final KotlinScopeTypes types;
  final KotlinScopeBodies bodies;
  final KotlinScopeExpressions expressions;

  KotlinScopeWalk(final ScopeBuilder builder) {
    this.types = new KotlinScopeTypes(this);
    this.bodies = new KotlinScopeBodies(this);
    this.expressions = new KotlinScopeExpressions(this);
    this.builder = builder;
    handlers.put("importHeader", types::importHeader);
    handlers.put("classDeclaration", types::classLike);
    handlers.put("objectDeclaration", types::classLike);
    handlers.put("companionObject", types::companion);
    handlers.put("classParameter", types::classParameter);
    handlers.put("enumEntry", types::enumEntry);
    handlers.put("typeAlias", types::typeAlias);
    handlers.put("functionDeclaration", types::function);
    handlers.put("secondaryConstructor", node -> scoped(node, ScopeKind.FUNCTION, false));
    handlers.put("functionValueParameter", types::valueParameter);
    handlers.put("propertyDeclaration", bodies::property);
    handlers.put("getter", bodies::accessor);
    handlers.put("setter", bodies::accessor);
    handlers.put(BLOCK, node -> scoped(node, ScopeKind.BLOCK, false));
    handlers.put("forExpression", bodies::forLoop);
    handlers.put("whenExpression", node -> scoped(node, ScopeKind.BLOCK, false));
    handlers.put("catchBlock", bodies::catchBlock);
    handlers.put("functionLiteral", bodies::lambda);
    handlers.put("objectLiteral", bodies::objectLiteral);
    handlers.put(EXPRESSION, expressions::assignment);
    handlers.put(POSTFIX, expressions::postfix);
    handlers.put("prefixUnaryExpression", expressions::prefix);
    handlers.put("postfixUnaryOperation", expressions::operation);
    handlers.put(ATOMIC, expressions::atomic);
    handlers.put("valueArgument", expressions::argument);
    SKIPPED.forEach(kind -> handlers.put(kind, node -> {}));
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  void generic(final SyntaxNode node) {
    node.children().forEach(this::visit);
  }

  void scoped(final SyntaxNode node, final ScopeKind kind, final boolean open) {
    final int scope = builder.open(kind, node.span());
    if (open) {
      builder.markOpen(scope);
    }
    generic(node);
    builder.close();
  }

  static String text(final SyntaxNode node) {
    return JavaSyntax.dotted(node);
  }

  static SyntaxNode simple(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> SIMPLE.equals(child.kind()))
        .findFirst()
        .orElse(null);
  }

  static boolean has(final SyntaxNode node, final String kind) {
    return node.children().stream().anyMatch(child -> kind.equals(child.kind()));
  }

  void declare(
      final SyntaxNode name,
      final BindingKind kind,
      final boolean written,
      final Set<BindingFlag> flags) {
    builder.declare(text(name), kind, name.span(), flags);
    if (written) {
      builder.reference(text(name), name.span(), ReferenceAccess.WRITE);
    }
  }
}
