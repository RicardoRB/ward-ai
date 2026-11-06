package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Handles the properties, loops and lambdas of Kotlin bodies. */
final class KotlinScopeBodies {

  private static final String CLASS_BODY = "classBody";

  private static final String LAMBDA_PARAMETERS = "lambdaParameters";

  private static final String PARAMETER = "parameter";

  private static final String SETTER = "setter";

  private static final String MULTI_VARIABLE_DECLARATION = "multiVariableDeclaration";

  private static final String SIMPLE = "simpleIdentifier";
  private static final String BLOCK = "block";
  private static final String EXPRESSION = "expression";
  private static final String VARIABLE_DECLARATION = "variableDeclaration";

  private final KotlinScopeWalk walk;

  KotlinScopeBodies(final KotlinScopeWalk walk) {
    this.walk = walk;
  }

  void property(final SyntaxNode node) {
    final boolean constant =
        node.children().stream().anyMatch(c -> "val".equals(KotlinScopeWalk.text(c)));
    final boolean initialized =
        node.children().stream()
            .anyMatch(c -> EXPRESSION.equals(c.kind()) || "by".equals(KotlinScopeWalk.text(c)));
    node.children().stream()
        .filter(
            c ->
                EXPRESSION.equals(c.kind()) || "getter".equals(c.kind()) || SETTER.equals(c.kind()))
        .forEach(walk::visit);
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    if (node.children().stream()
        .anyMatch(
            c -> "modifierList".equals(c.kind()) && KotlinScopeWalk.text(c).contains("const"))) {
      flags.add(BindingFlag.CONST);
    }
    final BindingKind kind = constant ? BindingKind.CONSTANT : BindingKind.VARIABLE;
    for (final SyntaxNode child : node.children()) {
      if (VARIABLE_DECLARATION.equals(child.kind())) {
        walk.declare(KotlinScopeWalk.simple(child), kind, initialized, flags);
      } else if (MULTI_VARIABLE_DECLARATION.equals(child.kind())) {
        flags.add(BindingFlag.DESTRUCTURED);
        KotlinSyntax.children(child, VARIABLE_DECLARATION)
            .forEach(part -> walk.declare(KotlinScopeWalk.simple(part), kind, initialized, flags));
      }
    }
  }

  void accessor(final SyntaxNode node) {
    walk.builder.open(ScopeKind.FUNCTION, node.span());
    walk.builder.declare(
        "field", BindingKind.VARIABLE, node.span(), EnumSet.noneOf(BindingFlag.class));
    if (SETTER.equals(node.kind())) {
      walk.builder.declare(
          "value", BindingKind.PARAMETER, node.span(), EnumSet.noneOf(BindingFlag.class));
    }
    for (final SyntaxNode part : node.children()) {
      if (PARAMETER.equals(part.kind())) {
        walk.declare(KotlinScopeWalk.simple(part), BindingKind.PARAMETER, false, Set.of());
      } else if (SIMPLE.equals(part.kind()) && !SETTER.equals(KotlinScopeWalk.text(part))) {
        walk.declare(part, BindingKind.PARAMETER, false, Set.of());
      } else {
        walk.visit(part);
      }
    }
    walk.builder.close();
  }

  void forLoop(final SyntaxNode node) {
    walk.builder.open(ScopeKind.BLOCK, node.span());
    final Set<BindingFlag> flags = EnumSet.of(BindingFlag.DESTRUCTURED);
    node.children().stream().filter(c -> EXPRESSION.equals(c.kind())).forEach(walk::visit);
    for (final SyntaxNode child : node.children()) {
      if (VARIABLE_DECLARATION.equals(child.kind())) {
        walk.declare(KotlinScopeWalk.simple(child), BindingKind.VARIABLE, true, flags);
      } else if (MULTI_VARIABLE_DECLARATION.equals(child.kind())) {
        KotlinSyntax.children(child, VARIABLE_DECLARATION)
            .forEach(
                part ->
                    walk.declare(KotlinScopeWalk.simple(part), BindingKind.VARIABLE, true, flags));
      } else if (!EXPRESSION.equals(child.kind())) {
        walk.visit(child);
      }
    }
    walk.builder.close();
  }

  void catchBlock(final SyntaxNode node) {
    walk.builder.open(ScopeKind.BLOCK, node.span());
    walk.declare(KotlinScopeWalk.simple(node), BindingKind.PARAMETER, false, Set.of());
    node.children().stream().filter(c -> BLOCK.equals(c.kind())).forEach(walk::visit);
    walk.builder.close();
  }

  void lambda(final SyntaxNode node) {
    final Optional<SyntaxNode> parameters =
        node.children().stream().filter(c -> LAMBDA_PARAMETERS.equals(c.kind())).findFirst();
    final boolean explicit = parameters.isPresent() && !parameters.get().children().isEmpty();
    final int scope = walk.builder.open(ScopeKind.LAMBDA, node.span());
    if (!explicit) {
      walk.builder.markOpen(scope);
      walk.builder.declare(
          "it", BindingKind.PARAMETER, node.span(), EnumSet.noneOf(BindingFlag.class));
    }
    for (final SyntaxNode part : node.children()) {
      if (LAMBDA_PARAMETERS.equals(part.kind())) {
        lambdaParameters(part);
      } else {
        walk.visit(part);
      }
    }
    walk.builder.close();
  }

  void lambdaParameters(final SyntaxNode node) {
    for (final SyntaxNode parameter : KotlinSyntax.children(node, "lambdaParameter")) {
      for (final SyntaxNode child : parameter.children()) {
        if (VARIABLE_DECLARATION.equals(child.kind())) {
          walk.declare(KotlinScopeWalk.simple(child), BindingKind.PARAMETER, false, Set.of());
        } else if (MULTI_VARIABLE_DECLARATION.equals(child.kind())) {
          KotlinSyntax.children(child, VARIABLE_DECLARATION)
              .forEach(
                  part ->
                      walk.declare(
                          KotlinScopeWalk.simple(part),
                          BindingKind.PARAMETER,
                          false,
                          Set.of(BindingFlag.DESTRUCTURED)));
        }
      }
    }
  }

  void objectLiteral(final SyntaxNode node) {
    for (final SyntaxNode part : node.children()) {
      if (CLASS_BODY.equals(part.kind())) {
        walk.scoped(part, ScopeKind.CLASS, true);
      } else {
        walk.visit(part);
      }
    }
  }
}
