package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** The scopes of the locals, loop variables, query variables and pattern variables of a body. */
final class CSharpScopeStatements {
  private static final Set<String> RANGE_VARIABLES =
      Set.of("from_clause", "let_clause", "join_clause", "join_into_clause", "query_continuation");
  private static final Set<String> EXPRESSION_VARIABLES =
      Set.of("single_variable_designation", "declaration_expression", "deconstruction_element");
  private static final Set<String> DECLARATORS =
      Set.of("variable_declarator", "local_variable_declarator");
  private static final Set<String> CONSTANTS =
      Set.of("constant_declarator", "enum_member_declaration");

  private final CSharpScopeWalk walk;

  CSharpScopeStatements(final CSharpScopeWalk walk) {
    this.walk = walk;
  }

  void register(final Map<String, Consumer<SyntaxNode>> handlers) {
    DECLARATORS.forEach(kind -> handlers.put(kind, node -> declarator(node, BindingKind.VARIABLE)));
    CONSTANTS.forEach(kind -> handlers.put(kind, node -> declarator(node, BindingKind.CONSTANT)));
    RANGE_VARIABLES.forEach(kind -> handlers.put(kind, this::rangeVariables));
    EXPRESSION_VARIABLES.forEach(kind -> handlers.put(kind, this::expressionVariable));
    handlers.put("foreach_statement", this::foreach);
    handlers.put("exception_specifier", this::exceptionVariable);
    handlers.put("labeled_statement", this::labeled);
    handlers.put("using_directive", this::usingDirective);
    handlers.put("goto_statement", walk::visitUnnamed);
  }

  private void declarator(final SyntaxNode node, final BindingKind kind) {
    final boolean initialized = CSharpSyntax.hasToken(node, "=");
    CSharpSyntax.name(node).ifPresent(name -> walk.declare(name, kind, initialized));
    walk.visitUnnamed(node);
  }

  private void foreach(final SyntaxNode node) {
    walk.builder().open(ScopeKind.BLOCK, node.span());
    CSharpSyntax.name(node)
        .ifPresent(
            name -> walk.declare(name, BindingKind.VARIABLE, false, BindingFlag.DESTRUCTURED)
        );
    walk.visitUnnamed(node);
    walk.builder().close();
  }

  private void rangeVariables(final SyntaxNode node) {
    CSharpSyntax.children(node, CSharpSyntax.IDENTIFIER)
        .forEach(name -> walk.declare(name, BindingKind.VARIABLE, true, BindingFlag.DESTRUCTURED));
    walk.visitUnnamed(node);
  }

  private void exceptionVariable(final SyntaxNode node) {
    CSharpSyntax.name(node).ifPresent(name -> walk.declare(name, BindingKind.PARAMETER, false));
  }

  private void expressionVariable(final SyntaxNode node) {
    final Optional<SyntaxNode> name =
        CSharpSyntax.name(node)
            .or(() -> CSharpSyntax.child(node, "designation").flatMap(CSharpSyntax::name));
    name.ifPresent(
        found -> walk.declare(found, BindingKind.VARIABLE, true, BindingFlag.DESTRUCTURED)
    );
    walk.generic(node);
  }

  private void labeled(final SyntaxNode node) {
    CSharpSyntax.child(node, "statement").ifPresent(walk::visit);
  }

  private void usingDirective(final SyntaxNode node) {
    final Optional<SyntaxNode> alias = CSharpSyntax.name(node);
    final Optional<SyntaxNode> target = CSharpSyntax.child(node, "type_");
    if (alias.isPresent() && target.isPresent()) {
      final SyntaxNode type = target.orElseThrow();
      final SyntaxNode name = alias.orElseThrow();
      walk.builder()
          .declareImport(
              CSharpSyntax.text(name),
              name.span(),
              new ImportOrigin(CSharpSyntax.text(type), "", type.span()),
              EnumSet.noneOf(BindingFlag.class)
          );
    }
  }
}
