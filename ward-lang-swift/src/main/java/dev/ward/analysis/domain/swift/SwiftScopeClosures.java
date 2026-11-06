package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Handles closures and the accessors of computed properties. */
final class SwiftScopeClosures {

  private static final String STATEMENT = "statement";

  private static final Set<String> ACCESSORS = Set.of("get", "set", "willSet", "didSet");
  private static final String IDENTIFIER = "declaration_identifier";

  private final SwiftScopeWalk walk;

  SwiftScopeClosures(final SwiftScopeWalk walk) {
    this.walk = walk;
  }

  /**
   * The grammar reads the accessors of a computed property, {@code { get { ... } set { ... } }}, as
   * a code block of calls with trailing closures, so a block made only of those is a list of
   * accessors.
   */
  void bodyOrAccessors(final SyntaxNode node) {
    final List<SyntaxNode> accessors =
        "code_block".equals(node.kind()) ? accessors(node) : List.of();
    if (accessors.isEmpty()) {
      walk.visit(node);
      return;
    }
    accessors.forEach(this::accessorCall);
  }

  static List<SyntaxNode> accessors(final SyntaxNode block) {
    final List<SyntaxNode> statements = new java.util.ArrayList<>();
    collectStatements(block, statements);
    final boolean allAccessors =
        !statements.isEmpty() && statements.stream().allMatch(SwiftScopeClosures::isAccessorCall);
    return allAccessors ? statements : List.of();
  }

  static void collectStatements(final SyntaxNode node, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if (STATEMENT.equals(child.kind())) {
        out.add(child);
      } else if ("statements".equals(child.kind()) || "statements_impl".equals(child.kind())) {
        collectStatements(child, out);
      }
    }
  }

  static boolean isAccessorCall(final SyntaxNode statement) {
    return ACCESSORS.contains(SwiftScopeWalk.text(statement).replaceAll("[({\\s].*", ""))
        && SwiftScopeWalk.findKind(statement, "trailing_closure") != null;
  }

  void accessorCall(final SyntaxNode statement) {
    final String name = SwiftScopeWalk.text(statement).replaceAll("[({\\s].*", "");
    walk.builder.open(ScopeKind.FUNCTION, statement.span());
    final String implicit = "didSet".equals(name) ? "oldValue" : "newValue";
    final SyntaxNode argument = SwiftScopeWalk.findKind(statement, "function_call_argument");
    final SyntaxNode explicit =
        argument == null
            ? null
            : walk.expressions.plainName(argument.children().get(argument.children().size() - 1));
    if (!"get".equals(name)) {
      if (explicit == null) {
        walk.builder.declare(
            implicit, BindingKind.PARAMETER, statement.span(), EnumSet.noneOf(BindingFlag.class));
      } else {
        walk.declare(explicit, BindingKind.PARAMETER, false, false);
      }
    }
    final SyntaxNode closure = SwiftScopeWalk.findKind(statement, "closure_expression");
    if (closure != null) {
      closure.children().stream().filter(c -> "statements".equals(c.kind())).forEach(walk::visit);
    }
    walk.builder.close();
  }

  void accessor(final SyntaxNode node, final String implicit) {
    walk.builder.open(ScopeKind.FUNCTION, node.span());
    final SyntaxNode named = SwiftScopeWalk.child(node, "setter_name");
    if (named == null) {
      walk.builder.declare(
          implicit, BindingKind.PARAMETER, node.span(), EnumSet.noneOf(BindingFlag.class));
    } else {
      walk.declare(SwiftScopeWalk.child(named, IDENTIFIER), BindingKind.PARAMETER, false, false);
    }
    node.children().stream().filter(c -> "code_block".equals(c.kind())).forEach(walk::visit);
    walk.builder.close();
  }

  void closure(final SyntaxNode node) {
    final boolean signature = SwiftScopeWalk.child(node, "closure_signature") != null;
    final int scope = walk.builder.open(ScopeKind.LAMBDA, node.span());
    if (!signature) {
      walk.builder.markOpen(scope);
    }
    walk.generic(node);
    walk.builder.close();
  }

  void closureParameter(final SyntaxNode node) {
    walk.declare(node, BindingKind.PARAMETER, false, false);
  }

  void closureIdentifiers(final SyntaxNode node) {
    node.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .forEach(name -> walk.declare(name, BindingKind.PARAMETER, false, false));
  }
}
