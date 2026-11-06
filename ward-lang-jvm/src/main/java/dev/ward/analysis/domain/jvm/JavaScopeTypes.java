package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Handles imports, types, members and variable declarations of Java. */
final class JavaScopeTypes {

  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "expression";
  private static final String DECLARATOR_ID = "variableDeclaratorId";

  private final JavaScopeWalk walk;

  JavaScopeTypes(final JavaScopeWalk walk) {
    this.walk = walk;
  }

  void importDeclaration(final SyntaxNode node) {
    final SyntaxNode name = JavaSyntax.child(node, "qualifiedName");
    final List<SyntaxNode> segments =
        name.children().stream().filter(c -> IDENTIFIER.equals(c.kind())).toList();
    if (JavaSyntax.hasTerminal(node, "*")) {
      if (JavaSyntax.hasTerminal(node, "static")) {
        walk.builder.markOpen(UnitScopes.MODULE_ID);
      }
      return;
    }
    final SyntaxNode last = segments.get(segments.size() - 1);
    walk.builder.declareImport(
        JavaScopeWalk.text(last),
        last.span(),
        new ImportOrigin(JavaScopeWalk.text(name), JavaScopeWalk.text(last), node.span()),
        EnumSet.noneOf(BindingFlag.class));
  }

  void typeDeclaration(final SyntaxNode node) {
    final SyntaxNode name = JavaScopeWalk.identifierOf(node);
    if (name != null) {
      walk.builder.declare(JavaScopeWalk.text(name), BindingKind.CLASS, name.span(), Set.of());
    }
    final int scope = walk.builder.open(ScopeKind.CLASS, node.span());
    final boolean inherits = !"classDeclaration".equals(node.kind()) || hasSupertype(node);
    if (inherits) {
      walk.builder.markOpen(scope);
    }
    walk.generic(node);
    walk.builder.close();
  }

  static boolean hasSupertype(final SyntaxNode classDeclaration) {
    return classDeclaration.children().stream()
        .anyMatch(child -> "typeList".equals(child.kind()) || "typeType".equals(child.kind()));
  }

  void member(final SyntaxNode node) {
    walk.finalMember =
        node.children().stream()
            .filter(child -> "modifier".equals(child.kind()))
            .anyMatch(child -> "final".equals(JavaScopeWalk.text(child)));
    walk.generic(node);
    walk.finalMember = false;
  }

  void method(final SyntaxNode node) {
    final SyntaxNode name = JavaScopeWalk.identifierOf(node);
    if (name != null
        && !"constructorDeclaration".equals(node.kind())
        && !"compactConstructorDeclaration".equals(node.kind())) {
      walk.builder.declare(JavaScopeWalk.text(name), BindingKind.FUNCTION, name.span(), Set.of());
    }
    walk.scoped(node, ScopeKind.FUNCTION);
  }

  void declarator(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode id = JavaSyntax.child(node, DECLARATOR_ID);
    final SyntaxNode name = JavaScopeWalk.identifierOf(id);
    final boolean initialized = parts.size() > 1;
    final boolean constant = walk.finalMember || walk.finalLocal;
    declare(
        name, constant ? BindingKind.CONSTANT : BindingKind.VARIABLE, constant, initialized, false);
    parts.stream().filter(part -> !DECLARATOR_ID.equals(part.kind())).forEach(walk::visit);
  }

  void constant(final SyntaxNode node) {
    declare(JavaScopeWalk.identifierOf(node), BindingKind.CONSTANT, true, true, false);
    node.children().stream().filter(part -> !IDENTIFIER.equals(part.kind())).forEach(walk::visit);
  }

  void declare(
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
    walk.builder.declare(JavaScopeWalk.text(name), kind, name.span(), flags);
    if (written) {
      walk.builder.reference(JavaScopeWalk.text(name), name.span(), ReferenceAccess.WRITE);
    }
  }

  void localVariable(final SyntaxNode node) {
    walk.finalLocal =
        node.children().stream()
            .filter(child -> "variableModifier".equals(child.kind()))
            .anyMatch(child -> "final".equals(JavaScopeWalk.text(child)));
    final SyntaxNode name = JavaScopeWalk.identifierOf(node);
    if (name != null) {
      node.children().stream()
          .filter(child -> EXPRESSION.equals(child.kind()))
          .forEach(walk::visit);
      declare(
          name,
          walk.finalLocal ? BindingKind.CONSTANT : BindingKind.VARIABLE,
          walk.finalLocal,
          true,
          false);
    } else {
      walk.generic(node);
    }
    walk.finalLocal = false;
  }

  void recordComponent(final SyntaxNode node) {
    declare(JavaScopeWalk.identifierOf(node), BindingKind.VARIABLE, true, false, false);
  }
}
