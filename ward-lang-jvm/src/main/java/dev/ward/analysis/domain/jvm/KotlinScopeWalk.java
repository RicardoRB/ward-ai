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
import java.util.Optional;
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

  private static final int TRIPLE = 3;

  private static final Set<String> CHAIN =
      Set.of(
          "expression",
          "disjunction",
          "conjunction",
          "equalityComparison",
          "comparison",
          "namedInfix",
          "elvisExpression",
          "infixFunctionCall",
          "rangeExpression",
          "additiveExpression",
          "multiplicativeExpression",
          "typeRHS",
          "prefixUnaryExpression",
          "postfixUnaryExpression",
          "atomicExpression");
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
  private static final String VARIABLE_DECLARATION = "variableDeclaration";

  private final ScopeBuilder builder;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();

  KotlinScopeWalk(final ScopeBuilder builder) {
    this.builder = builder;
    handlers.put("importHeader", this::importHeader);
    handlers.put("classDeclaration", this::classLike);
    handlers.put("objectDeclaration", this::classLike);
    handlers.put("companionObject", this::companion);
    handlers.put("classParameter", this::classParameter);
    handlers.put("enumEntry", this::enumEntry);
    handlers.put("typeAlias", this::typeAlias);
    handlers.put("functionDeclaration", this::function);
    handlers.put("secondaryConstructor", node -> scoped(node, ScopeKind.FUNCTION, false));
    handlers.put("functionValueParameter", this::valueParameter);
    handlers.put("propertyDeclaration", this::property);
    handlers.put("getter", this::accessor);
    handlers.put("setter", this::accessor);
    handlers.put(BLOCK, node -> scoped(node, ScopeKind.BLOCK, false));
    handlers.put("forExpression", this::forLoop);
    handlers.put("whenExpression", node -> scoped(node, ScopeKind.BLOCK, false));
    handlers.put("catchBlock", this::catchBlock);
    handlers.put("functionLiteral", this::lambda);
    handlers.put("objectLiteral", this::objectLiteral);
    handlers.put(EXPRESSION, this::assignment);
    handlers.put(POSTFIX, this::postfix);
    handlers.put("prefixUnaryExpression", this::prefix);
    handlers.put("postfixUnaryOperation", this::operation);
    handlers.put(ATOMIC, this::atomic);
    handlers.put("valueArgument", this::argument);
    SKIPPED.forEach(kind -> handlers.put(kind, node -> {}));
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  private void generic(final SyntaxNode node) {
    node.children().forEach(this::visit);
  }

  private void scoped(final SyntaxNode node, final ScopeKind kind, final boolean open) {
    final int scope = builder.open(kind, node.span());
    if (open) {
      builder.markOpen(scope);
    }
    generic(node);
    builder.close();
  }

  private static String text(final SyntaxNode node) {
    return JavaSyntax.dotted(node);
  }

  private static SyntaxNode simple(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> SIMPLE.equals(child.kind()))
        .findFirst()
        .orElse(null);
  }

  private static boolean has(final SyntaxNode node, final String kind) {
    return node.children().stream().anyMatch(child -> kind.equals(child.kind()));
  }

  private void declare(
      final SyntaxNode name,
      final BindingKind kind,
      final boolean written,
      final Set<BindingFlag> flags) {
    builder.declare(text(name), kind, name.span(), flags);
    if (written) {
      builder.reference(text(name), name.span(), ReferenceAccess.WRITE);
    }
  }

  private void importHeader(final SyntaxNode node) {
    final SyntaxNode path = JavaSyntax.child(node, "identifier");
    final List<SyntaxNode> segments = KotlinSyntax.children(path, SIMPLE);
    if (node.children().stream().anyMatch(child -> "*".equals(text(child)))) {
      builder.markOpen(UnitScopes.MODULE_ID);
      return;
    }
    final SyntaxNode last = segments.get(segments.size() - 1);
    final Optional<SyntaxNode> alias =
        node.children().stream().filter(child -> "importAlias".equals(child.kind())).findFirst();
    final SyntaxNode bound = alias.map(KotlinScopeWalk::simple).orElse(last);
    builder.declareImport(
        text(bound),
        bound.span(),
        new ImportOrigin(text(path), text(last), node.span()),
        EnumSet.noneOf(BindingFlag.class));
  }

  private void classLike(final SyntaxNode node) {
    final SyntaxNode name = simple(node);
    if (name != null) {
      declare(name, BindingKind.CLASS, false, Set.of());
    }
    final boolean inherits =
        has(node, "delegationSpecifiers")
            || has(node, "enumClassBody")
            || node.children().stream().anyMatch(child -> "interface".equals(text(child)));
    scoped(node, ScopeKind.CLASS, inherits);
  }

  private void companion(final SyntaxNode node) {
    builder.markOpen(builder.current());
    final SyntaxNode name = simple(node);
    if (name != null) {
      declare(name, BindingKind.CLASS, false, Set.of());
    }
    scoped(node, ScopeKind.CLASS, has(node, "delegationSpecifiers"));
  }

  private void classParameter(final SyntaxNode node) {
    final SyntaxNode name = simple(node);
    final boolean property =
        node.children().stream().anyMatch(c -> "val".equals(text(c)) || "var".equals(text(c)));
    final boolean constant = node.children().stream().anyMatch(c -> "val".equals(text(c)));
    declare(
        name,
        property ? (constant ? BindingKind.CONSTANT : BindingKind.VARIABLE) : BindingKind.PARAMETER,
        false,
        Set.of());
    node.children().stream().filter(c -> EXPRESSION.equals(c.kind())).forEach(this::visit);
  }

  private void enumEntry(final SyntaxNode node) {
    declare(simple(node), BindingKind.CONSTANT, false, EnumSet.of(BindingFlag.CONST));
    node.children().stream().filter(c -> !SIMPLE.equals(c.kind())).forEach(this::visit);
  }

  private void typeAlias(final SyntaxNode node) {
    declare(simple(node), BindingKind.TYPE, false, EnumSet.of(BindingFlag.TYPE_ONLY));
  }

  private void function(final SyntaxNode node) {
    final Optional<SyntaxNode> name =
        node.children().stream().filter(child -> "identifier".equals(child.kind())).findFirst();
    name.ifPresent(
        identifier -> {
          final SyntaxNode last = identifier.children().get(identifier.children().size() - 1);
          builder.declare(text(last), BindingKind.FUNCTION, last.span(), Set.of());
        });
    final boolean extension = has(node, "receiverType") || hasReceiver(node);
    scoped(node, ScopeKind.FUNCTION, extension);
  }

  /** A function written {@code fun Type.name} has a receiver, whose members are in scope. */
  private static boolean hasReceiver(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if ("type".equals(parts.get(index).kind()) && ".".equals(text(parts.get(index + 1)))) {
        return true;
      }
    }
    return false;
  }

  private void valueParameter(final SyntaxNode node) {
    final SyntaxNode parameter = JavaSyntax.child(node, "parameter");
    declare(simple(parameter), BindingKind.PARAMETER, false, Set.of());
    node.children().stream().filter(c -> EXPRESSION.equals(c.kind())).forEach(this::visit);
  }

  private void property(final SyntaxNode node) {
    final boolean constant = node.children().stream().anyMatch(c -> "val".equals(text(c)));
    final boolean initialized =
        node.children().stream().anyMatch(c -> EXPRESSION.equals(c.kind()) || "by".equals(text(c)));
    node.children().stream()
        .filter(
            c ->
                EXPRESSION.equals(c.kind())
                    || "getter".equals(c.kind())
                    || "setter".equals(c.kind()))
        .forEach(this::visit);
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    if (node.children().stream()
        .anyMatch(c -> "modifierList".equals(c.kind()) && text(c).contains("const"))) {
      flags.add(BindingFlag.CONST);
    }
    final BindingKind kind = constant ? BindingKind.CONSTANT : BindingKind.VARIABLE;
    for (final SyntaxNode child : node.children()) {
      if (VARIABLE_DECLARATION.equals(child.kind())) {
        declare(simple(child), kind, initialized, flags);
      } else if ("multiVariableDeclaration".equals(child.kind())) {
        flags.add(BindingFlag.DESTRUCTURED);
        KotlinSyntax.children(child, VARIABLE_DECLARATION)
            .forEach(part -> declare(simple(part), kind, initialized, flags));
      }
    }
  }

  private void accessor(final SyntaxNode node) {
    builder.open(ScopeKind.FUNCTION, node.span());
    builder.declare("field", BindingKind.VARIABLE, node.span(), EnumSet.noneOf(BindingFlag.class));
    if ("setter".equals(node.kind())) {
      builder.declare(
          "value", BindingKind.PARAMETER, node.span(), EnumSet.noneOf(BindingFlag.class));
    }
    for (final SyntaxNode part : node.children()) {
      if ("parameter".equals(part.kind())) {
        declare(simple(part), BindingKind.PARAMETER, false, Set.of());
      } else if (SIMPLE.equals(part.kind()) && !"setter".equals(text(part))) {
        declare(part, BindingKind.PARAMETER, false, Set.of());
      } else {
        visit(part);
      }
    }
    builder.close();
  }

  private void forLoop(final SyntaxNode node) {
    builder.open(ScopeKind.BLOCK, node.span());
    final Set<BindingFlag> flags = EnumSet.of(BindingFlag.DESTRUCTURED);
    node.children().stream().filter(c -> EXPRESSION.equals(c.kind())).forEach(this::visit);
    for (final SyntaxNode child : node.children()) {
      if (VARIABLE_DECLARATION.equals(child.kind())) {
        declare(simple(child), BindingKind.VARIABLE, true, flags);
      } else if ("multiVariableDeclaration".equals(child.kind())) {
        KotlinSyntax.children(child, VARIABLE_DECLARATION)
            .forEach(part -> declare(simple(part), BindingKind.VARIABLE, true, flags));
      } else if (!EXPRESSION.equals(child.kind())) {
        visit(child);
      }
    }
    builder.close();
  }

  private void catchBlock(final SyntaxNode node) {
    builder.open(ScopeKind.BLOCK, node.span());
    declare(simple(node), BindingKind.PARAMETER, false, Set.of());
    node.children().stream().filter(c -> BLOCK.equals(c.kind())).forEach(this::visit);
    builder.close();
  }

  private void lambda(final SyntaxNode node) {
    final Optional<SyntaxNode> parameters =
        node.children().stream().filter(c -> "lambdaParameters".equals(c.kind())).findFirst();
    final boolean explicit = parameters.isPresent() && !parameters.get().children().isEmpty();
    final int scope = builder.open(ScopeKind.LAMBDA, node.span());
    if (!explicit) {
      builder.markOpen(scope);
      builder.declare("it", BindingKind.PARAMETER, node.span(), EnumSet.noneOf(BindingFlag.class));
    }
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
    for (final SyntaxNode parameter : KotlinSyntax.children(node, "lambdaParameter")) {
      for (final SyntaxNode child : parameter.children()) {
        if (VARIABLE_DECLARATION.equals(child.kind())) {
          declare(simple(child), BindingKind.PARAMETER, false, Set.of());
        } else if ("multiVariableDeclaration".equals(child.kind())) {
          KotlinSyntax.children(child, VARIABLE_DECLARATION)
              .forEach(
                  part ->
                      declare(
                          simple(part),
                          BindingKind.PARAMETER,
                          false,
                          Set.of(BindingFlag.DESTRUCTURED)));
        }
      }
    }
  }

  private void objectLiteral(final SyntaxNode node) {
    for (final SyntaxNode part : node.children()) {
      if ("classBody".equals(part.kind())) {
        scoped(part, ScopeKind.CLASS, true);
      } else {
        visit(part);
      }
    }
  }

  /** The name an expression chain is made of alone, or null. */
  private static SyntaxNode plainName(final SyntaxNode expression) {
    SyntaxNode current = expression;
    while (CHAIN.contains(current.kind()) && current.children().size() == 1) {
      current = current.children().get(0);
    }
    return SIMPLE.equals(current.kind()) ? current : null;
  }

  private void assignment(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == TRIPLE && "assignmentOperator".equals(parts.get(1).kind())) {
      target(parts.get(0), !"=".equals(text(parts.get(1))));
      visit(parts.get(2));
    } else {
      generic(node);
    }
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

  private void postfix(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final boolean update =
        parts.size() == 2
            && ("++".equals(text(parts.get(1))) || "--".equals(text(parts.get(1))))
            && plainName(parts.get(0)) != null;
    if (update) {
      target(parts.get(0), true);
    } else {
      generic(node);
    }
  }

  private void prefix(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final boolean update =
        parts.size() == 2
            && ("++".equals(text(parts.get(0))) || "--".equals(text(parts.get(0))))
            && plainName(parts.get(1)) != null;
    if (update) {
      target(parts.get(1), true);
    } else {
      generic(node);
    }
  }

  /** A member access {@code a.b}: the name after the dot is not a reference. */
  private void operation(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (!parts.isEmpty()
        && "memberAccessOperator".equals(parts.get(0).kind())
        && parts.size() > 1) {
      final SyntaxNode member = parts.get(parts.size() - 1);
      member.children().stream().skip(1).forEach(this::visit);
    } else {
      generic(node);
    }
  }

  private void atomic(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == 1 && SIMPLE.equals(parts.get(0).kind())) {
      builder.reference(text(parts.get(0)), parts.get(0).span(), ReferenceAccess.READ);
    } else if (parts.size() == 2 && "val".equals(text(parts.get(0)))) {
      declare(parts.get(1), BindingKind.CONSTANT, true, EnumSet.noneOf(BindingFlag.class));
    } else {
      generic(node);
    }
  }

  private void argument(final SyntaxNode node) {
    node.children().stream().filter(c -> EXPRESSION.equals(c.kind())).forEach(this::visit);
  }
}
