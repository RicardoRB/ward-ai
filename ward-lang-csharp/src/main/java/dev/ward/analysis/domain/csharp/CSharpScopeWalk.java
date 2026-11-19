package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Walks a C# syntax tree and records its scopes. Types are not references: a type name is a
 * {@code type_name}, and only a {@code simple_name} in an expression is a use. A local is in scope
 * in its whole block, as the compiler has it, so a use before the declaration resolves to the local
 * and is flagged as before. An expression variable (a pattern or {@code out} variable) belongs to
 * the nearest statement scope. The declarations of types and members, the statements and the
 * expressions are handled by their own classes, which share this walk.
 */
final class CSharpScopeWalk {
  private static final Set<String> BLOCKS =
      Set.of(
          "block",
          "namespace_declaration",
          "foreach_statement",
          "for_statement",
          "using_statement",
          "if_statement",
          "while_statement",
          "switch_section",
          "switch_expression_arm",
          "specific_catch_clause",
          "query_expression"
      );
  private static final Set<String> FUNCTIONS =
      Set.of(
          "constructor_declaration",
          "finalizer_declaration",
          "operator_declaration",
          "indexer_declaration",
          "delegate_declaration"
      );
  private static final Set<String> LAMBDAS =
      Set.of("lambda_expression", "anonymous_method_expression");

  private final ScopeBuilder builder;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();

  CSharpScopeWalk(final ScopeBuilder builder) {
    this.builder = builder;
    BLOCKS.forEach(kind -> handlers.put(kind, node -> scoped(node, ScopeKind.BLOCK, false)));
    FUNCTIONS.forEach(kind -> handlers.put(kind, node -> scoped(node, ScopeKind.FUNCTION, false)));
    LAMBDAS.forEach(kind -> handlers.put(kind, node -> scoped(node, ScopeKind.LAMBDA, false)));
    handlers.put("type_", node -> {});
    handlers.put("member_name", node -> {});
    new CSharpScopeTypes(this).register(handlers);
    new CSharpScopeStatements(this).register(handlers);
    new CSharpScopeExpressions(this).register(handlers);
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  void generic(final SyntaxNode node) {
    node.children().forEach(this::visit);
  }

  /** Visits the children of {@code node} that are not its own {@code identifier}. */
  void visitUnnamed(final SyntaxNode node) {
    node.children().stream()
        .filter(child -> !CSharpSyntax.IDENTIFIER.equals(child.kind()))
        .forEach(this::visit);
  }

  ScopeBuilder builder() {
    return builder;
  }

  void scoped(final SyntaxNode node, final ScopeKind kind, final boolean open) {
    final int scope = builder.open(kind, node.span());
    if (open) {
      builder.markOpen(scope);
    }
    generic(node);
    builder.close();
  }

  void declare(final SyntaxNode name, final BindingKind kind, final boolean written) {
    declare(name, kind, written, null);
  }

  void declare(
      final SyntaxNode name,
      final BindingKind kind,
      final boolean written,
      final BindingFlag flag
  ) {
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    if (flag != null) {
      flags.add(flag);
    }
    final String text = CSharpSyntax.text(name);
    builder.declare(text, kind, name.span(), flags);
    if (written) {
      builder.reference(text, name.span(), ReferenceAccess.WRITE);
    }
  }
}
