package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.ReferenceAccess;
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
  private static final Set<String> ASSIGNMENTS =
      Set.of("=", "+=", "-=", "*=", "/=", "&=", "|=", "^=", ">>=", ">>>=", "<<=", "%=");
  private static final Set<String> UPDATES = Set.of("++", "--");
  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "expression";
  private static final String PRIMARY = "primary";
  private static final String DECLARATOR_ID = "variableDeclaratorId";

  private final ScopeBuilder builder;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();
  private boolean finalMember;
  private boolean finalLocal;

  JavaScopeWalk(final ScopeBuilder builder) {
    this.builder = builder;
    handlers.put("importDeclaration", this::importDeclaration);
    handlers.put("annotation", node -> {});
    handlers.put("classBodyDeclaration", this::member);
    handlers.put("interfaceBodyDeclaration", this::member);
    handlers.put("variableDeclarator", this::declarator);
    handlers.put("constantDeclarator", this::constant);
    handlers.put("localVariableDeclaration", this::localVariable);
    handlers.put("recordComponent", this::recordComponent);
    handlers.put("enumConstant", this::enumConstant);
    handlers.put("classCreatorRest", this::anonymousClass);
    handlers.put("formalParameter", this::parameter);
    handlers.put("block", node -> scoped(node, ScopeKind.BLOCK));
    handlers.put("statement", this::statement);
    handlers.put("enhancedForControl", this::enhancedFor);
    handlers.put("catchClause", this::catchClause);
    handlers.put("resource", this::resource);
    handlers.put("lambdaExpression", this::lambda);
    handlers.put(EXPRESSION, this::expression);
    handlers.put(PRIMARY, this::primary);
    handlers.put("methodCall", this::methodCall);
    handlers.put("switchLabeledRule", this::switchRule);
    handlers.put("switchLabel", this::switchRule);
    TYPE_DECLARATIONS.forEach(kind -> handlers.put(kind, this::typeDeclaration));
    METHODS.forEach(kind -> handlers.put(kind, this::method));
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  private void generic(final SyntaxNode node) {
    node.children().forEach(this::visit);
  }

  private void scoped(final SyntaxNode node, final ScopeKind kind) {
    builder.open(kind, node.span());
    generic(node);
    builder.close();
  }

  private static String text(final SyntaxNode node) {
    return JavaSyntax.dotted(node);
  }

  private static SyntaxNode identifierOf(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .orElse(null);
  }

  private void importDeclaration(final SyntaxNode node) {
    final SyntaxNode name = JavaSyntax.child(node, "qualifiedName");
    final List<SyntaxNode> segments =
        name.children().stream().filter(c -> IDENTIFIER.equals(c.kind())).toList();
    if (JavaSyntax.hasTerminal(node, "*")) {
      if (JavaSyntax.hasTerminal(node, "static")) {
        builder.markOpen(UnitScopes.MODULE_ID);
      }
      return;
    }
    final SyntaxNode last = segments.get(segments.size() - 1);
    builder.declareImport(
        text(last),
        last.span(),
        new ImportOrigin(text(name), text(last), node.span()),
        EnumSet.noneOf(BindingFlag.class));
  }

  private void typeDeclaration(final SyntaxNode node) {
    final SyntaxNode name = identifierOf(node);
    if (name != null) {
      builder.declare(text(name), BindingKind.CLASS, name.span(), Set.of());
    }
    final int scope = builder.open(ScopeKind.CLASS, node.span());
    final boolean inherits = !"classDeclaration".equals(node.kind()) || hasSupertype(node);
    if (inherits) {
      builder.markOpen(scope);
    }
    generic(node);
    builder.close();
  }

  private static boolean hasSupertype(final SyntaxNode classDeclaration) {
    return classDeclaration.children().stream()
        .anyMatch(child -> "typeList".equals(child.kind()) || "typeType".equals(child.kind()));
  }

  private void member(final SyntaxNode node) {
    finalMember =
        node.children().stream()
            .filter(child -> "modifier".equals(child.kind()))
            .anyMatch(child -> "final".equals(text(child)));
    generic(node);
    finalMember = false;
  }

  private void method(final SyntaxNode node) {
    final SyntaxNode name = identifierOf(node);
    if (name != null
        && !"constructorDeclaration".equals(node.kind())
        && !"compactConstructorDeclaration".equals(node.kind())) {
      builder.declare(text(name), BindingKind.FUNCTION, name.span(), Set.of());
    }
    scoped(node, ScopeKind.FUNCTION);
  }

  private void declarator(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode id = JavaSyntax.child(node, DECLARATOR_ID);
    final SyntaxNode name = identifierOf(id);
    final boolean initialized = parts.size() > 1;
    final boolean constant = finalMember || finalLocal;
    declare(
        name, constant ? BindingKind.CONSTANT : BindingKind.VARIABLE, constant, initialized, false);
    parts.stream().filter(part -> !DECLARATOR_ID.equals(part.kind())).forEach(this::visit);
  }

  private void constant(final SyntaxNode node) {
    declare(identifierOf(node), BindingKind.CONSTANT, true, true, false);
    node.children().stream().filter(part -> !IDENTIFIER.equals(part.kind())).forEach(this::visit);
  }

  private void declare(
      final SyntaxNode name,
      final BindingKind kind,
      final boolean constant,
      final boolean written,
      final boolean destructured) {
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    if (constant) {
      flags.add(BindingFlag.CONST);
    }
    if (destructured) {
      flags.add(BindingFlag.DESTRUCTURED);
    }
    builder.declare(text(name), kind, name.span(), flags);
    if (written) {
      builder.reference(text(name), name.span(), ReferenceAccess.WRITE);
    }
  }

  private void localVariable(final SyntaxNode node) {
    finalLocal =
        node.children().stream()
            .filter(child -> "variableModifier".equals(child.kind()))
            .anyMatch(child -> "final".equals(text(child)));
    final SyntaxNode name = identifierOf(node);
    if (name != null) {
      node.children().stream()
          .filter(child -> EXPRESSION.equals(child.kind()))
          .forEach(this::visit);
      declare(
          name, finalLocal ? BindingKind.CONSTANT : BindingKind.VARIABLE, finalLocal, true, false);
    } else {
      generic(node);
    }
    finalLocal = false;
  }

  private void recordComponent(final SyntaxNode node) {
    declare(identifierOf(node), BindingKind.VARIABLE, true, false, false);
  }

  private void enumConstant(final SyntaxNode node) {
    declare(identifierOf(node), BindingKind.CONSTANT, true, false, false);
    node.children().stream().filter(child -> !IDENTIFIER.equals(child.kind())).forEach(this::visit);
  }

  private void anonymousClass(final SyntaxNode node) {
    for (final SyntaxNode part : node.children()) {
      if ("classBody".equals(part.kind())) {
        final int scope = builder.open(ScopeKind.CLASS, part.span());
        builder.markOpen(scope);
        generic(part);
        builder.close();
      } else {
        visit(part);
      }
    }
  }

  private void parameter(final SyntaxNode node) {
    final SyntaxNode id =
        node.children().stream()
            .filter(c -> DECLARATOR_ID.equals(c.kind()))
            .findFirst()
            .orElse(null);
    if (id != null) {
      declare(identifierOf(id), BindingKind.PARAMETER, false, false, false);
    }
  }

  private void statement(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final boolean scoped =
        parts.stream()
                .anyMatch(
                    part ->
                        "forControl".equals(part.kind())
                            || "resourceSpecification".equals(part.kind()))
            || !parts.isEmpty() && "switch".equals(text(parts.get(0)));
    if (scoped) {
      scoped(node, ScopeKind.BLOCK);
    } else {
      generic(node);
    }
  }

  private void enhancedFor(final SyntaxNode node) {
    node.children().stream().filter(part -> EXPRESSION.equals(part.kind())).forEach(this::visit);
    final SyntaxNode id = JavaSyntax.child(node, DECLARATOR_ID);
    declare(identifierOf(id), BindingKind.VARIABLE, false, true, true);
  }

  private void catchClause(final SyntaxNode node) {
    builder.open(ScopeKind.BLOCK, node.span());
    final SyntaxNode name = identifierOf(node);
    if (name != null) {
      declare(name, BindingKind.PARAMETER, false, false, false);
    }
    node.children().stream().filter(part -> "block".equals(part.kind())).forEach(this::visit);
    builder.close();
  }

  private void resource(final SyntaxNode node) {
    final SyntaxNode id =
        node.children().stream()
            .filter(c -> DECLARATOR_ID.equals(c.kind()))
            .findFirst()
            .orElse(null);
    final SyntaxNode name = id == null ? identifierOf(node) : identifierOf(id);
    node.children().stream().filter(part -> EXPRESSION.equals(part.kind())).forEach(this::visit);
    if (name != null) {
      declare(name, BindingKind.VARIABLE, false, true, true);
    }
  }

  private void lambda(final SyntaxNode node) {
    builder.open(ScopeKind.LAMBDA, node.span());
    for (final SyntaxNode part : node.children()) {
      if ("lambdaParameters".equals(part.kind())) {
        lambdaParameters(part);
      } else {
        visit(part);
      }
    }
    builder.close();
  }

  private void lambdaParameters(final SyntaxNode node) {
    for (final SyntaxNode part : node.children()) {
      if (IDENTIFIER.equals(part.kind())) {
        declare(part, BindingKind.PARAMETER, false, false, false);
      } else {
        collectLambdaTyped(part);
      }
    }
  }

  private void collectLambdaTyped(final SyntaxNode node) {
    if (DECLARATOR_ID.equals(node.kind())) {
      declare(identifierOf(node), BindingKind.PARAMETER, false, false, false);
    } else if ("lambdaLVTIParameter".equals(node.kind())) {
      declare(identifierOf(node), BindingKind.PARAMETER, false, false, false);
    } else {
      node.children().forEach(this::collectLambdaTyped);
    }
  }

  private void expression(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == 3 && ASSIGNMENTS.contains(text(parts.get(1)))) {
      target(parts.get(0), !"=".equals(text(parts.get(1))));
      visit(parts.get(2));
    } else if (parts.size() == 2 && updates(parts)) {
      target(EXPRESSION.equals(parts.get(0).kind()) ? parts.get(0) : parts.get(1), true);
    } else if (parts.size() == 3 && "instanceof".equals(text(parts.get(1)))) {
      visit(parts.get(0));
      patternBinding(parts.get(2));
    } else {
      generic(node);
    }
  }

  private static boolean updates(final List<SyntaxNode> parts) {
    return UPDATES.contains(text(parts.get(0))) || UPDATES.contains(text(parts.get(1)));
  }

  /** The name an expression is made of alone, or null. */
  private static SyntaxNode plainName(final SyntaxNode expression) {
    SyntaxNode current = expression;
    while (current.children().size() == 1 && !IDENTIFIER.equals(current.kind())) {
      current = current.children().get(0);
    }
    return IDENTIFIER.equals(current.kind()) ? current : null;
  }

  private void target(final SyntaxNode expression, final boolean compound) {
    final SyntaxNode name = plainName(expression);
    if (name == null) {
      visit(expression);
      return;
    }
    if (compound) {
      builder.reference(text(name), name.span(), ReferenceAccess.READ);
    }
    builder.reference(text(name), name.span(), ReferenceAccess.WRITE);
  }

  /**
   * {@code o instanceof String s} binds {@code s}; the grammar reads it as a type with two names.
   */
  private void patternBinding(final SyntaxNode type) {
    final SyntaxNode pattern = findKind(type, "classType");
    if (pattern == null) {
      return;
    }
    final List<SyntaxNode> names = JavaSyntaxNodes.children(pattern, "typeIdentifier");
    if (names.size() > 1) {
      final SyntaxNode name = names.get(names.size() - 1);
      declare(name, BindingKind.VARIABLE, false, false, true);
    }
  }

  private static SyntaxNode findKind(final SyntaxNode node, final String kind) {
    if (kind.equals(node.kind())) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      final SyntaxNode found = findKind(child, kind);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private void primary(final SyntaxNode node) {
    final SyntaxNode name = node.children().size() == 1 ? identifierOf(node) : null;
    if (name == null) {
      generic(node);
    } else {
      builder.reference(text(name), name.span(), ReferenceAccess.READ);
    }
  }

  private void methodCall(final SyntaxNode node) {
    node.children().stream().filter(part -> !IDENTIFIER.equals(part.kind())).forEach(this::visit);
  }

  private void switchRule(final SyntaxNode node) {
    for (final SyntaxNode part : node.children()) {
      if ("expressionList".equals(part.kind())) {
        part.children().stream()
            .filter(label -> !EXPRESSION.equals(label.kind()) || plainName(label) == null)
            .forEach(this::visit);
      } else if (EXPRESSION.equals(part.kind()) && plainName(part) != null) {
        continue;
      } else {
        visit(part);
      }
    }
  }
}
