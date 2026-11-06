package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Handles imports, types, members and functions of Kotlin. */
final class KotlinScopeTypes {

  private static final String SIMPLE = "simpleIdentifier";
  private static final String EXPRESSION = "expression";

  private final KotlinScopeWalk walk;

  KotlinScopeTypes(final KotlinScopeWalk walk) {
    this.walk = walk;
  }

  void importHeader(final SyntaxNode node) {
    final SyntaxNode path = JavaSyntax.child(node, "identifier");
    final List<SyntaxNode> segments = KotlinSyntax.children(path, SIMPLE);
    if (node.children().stream().anyMatch(child -> "*".equals(KotlinScopeWalk.text(child)))) {
      walk.builder.markOpen(UnitScopes.MODULE_ID);
      return;
    }
    final SyntaxNode last = segments.get(segments.size() - 1);
    final Optional<SyntaxNode> alias =
        node.children().stream().filter(child -> "importAlias".equals(child.kind())).findFirst();
    final SyntaxNode bound = alias.map(KotlinScopeWalk::simple).orElse(last);
    walk.builder.declareImport(
        KotlinScopeWalk.text(bound),
        bound.span(),
        new ImportOrigin(KotlinScopeWalk.text(path), KotlinScopeWalk.text(last), node.span()),
        EnumSet.noneOf(BindingFlag.class));
  }

  void classLike(final SyntaxNode node) {
    final SyntaxNode name = KotlinScopeWalk.simple(node);
    if (name != null) {
      walk.declare(name, BindingKind.CLASS, false, Set.of());
    }
    final boolean inherits =
        KotlinScopeWalk.has(node, "delegationSpecifiers")
            || KotlinScopeWalk.has(node, "enumClassBody")
            || node.children().stream()
                .anyMatch(child -> "interface".equals(KotlinScopeWalk.text(child)));
    walk.scoped(node, ScopeKind.CLASS, inherits);
  }

  void companion(final SyntaxNode node) {
    walk.builder.markOpen(walk.builder.current());
    final SyntaxNode name = KotlinScopeWalk.simple(node);
    if (name != null) {
      walk.declare(name, BindingKind.CLASS, false, Set.of());
    }
    walk.scoped(node, ScopeKind.CLASS, KotlinScopeWalk.has(node, "delegationSpecifiers"));
  }

  void classParameter(final SyntaxNode node) {
    final SyntaxNode name = KotlinScopeWalk.simple(node);
    final boolean property =
        node.children().stream()
            .anyMatch(
                c ->
                    "val".equals(KotlinScopeWalk.text(c)) || "var".equals(KotlinScopeWalk.text(c)));
    final boolean constant =
        node.children().stream().anyMatch(c -> "val".equals(KotlinScopeWalk.text(c)));
    walk.declare(
        name,
        property ? (constant ? BindingKind.CONSTANT : BindingKind.VARIABLE) : BindingKind.PARAMETER,
        false,
        Set.of());
    node.children().stream().filter(c -> EXPRESSION.equals(c.kind())).forEach(walk::visit);
  }

  void enumEntry(final SyntaxNode node) {
    walk.declare(
        KotlinScopeWalk.simple(node), BindingKind.CONSTANT, false, EnumSet.of(BindingFlag.CONST));
    node.children().stream().filter(c -> !SIMPLE.equals(c.kind())).forEach(walk::visit);
  }

  void typeAlias(final SyntaxNode node) {
    walk.declare(
        KotlinScopeWalk.simple(node), BindingKind.TYPE, false, EnumSet.of(BindingFlag.TYPE_ONLY));
  }

  void function(final SyntaxNode node) {
    final Optional<SyntaxNode> name =
        node.children().stream().filter(child -> "identifier".equals(child.kind())).findFirst();
    name.ifPresent(
        identifier -> {
          final SyntaxNode last = identifier.children().get(identifier.children().size() - 1);
          walk.builder.declare(
              KotlinScopeWalk.text(last), BindingKind.FUNCTION, last.span(), Set.of());
        });
    final boolean extension = KotlinScopeWalk.has(node, "receiverType") || hasReceiver(node);
    walk.scoped(node, ScopeKind.FUNCTION, extension);
  }

  /** A function written {@code fun Type.name} has a receiver, whose members are in scope. */
  static boolean hasReceiver(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if ("type".equals(parts.get(index).kind())
          && ".".equals(KotlinScopeWalk.text(parts.get(index + 1)))) {
        return true;
      }
    }
    return false;
  }

  void valueParameter(final SyntaxNode node) {
    final SyntaxNode parameter = JavaSyntax.child(node, "parameter");
    walk.declare(KotlinScopeWalk.simple(parameter), BindingKind.PARAMETER, false, Set.of());
    node.children().stream().filter(c -> EXPRESSION.equals(c.kind())).forEach(walk::visit);
  }
}
