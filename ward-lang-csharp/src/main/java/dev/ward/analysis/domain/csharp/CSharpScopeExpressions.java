package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Records the uses of names in the expressions of a C# file. */
final class CSharpScopeExpressions {
  private static final String SIMPLE_NAME = "simple_name";
  private static final int ASSIGNMENT_PARTS = 3;
  private static final int OPERATOR = 1;
  private static final int VALUE = 2;

  private final CSharpScopeWalk walk;

  CSharpScopeExpressions(final CSharpScopeWalk walk) {
    this.walk = walk;
  }

  void register(final Map<String, Consumer<SyntaxNode>> handlers) {
    handlers.put(SIMPLE_NAME, this::simpleName);
    handlers.put("expression", this::expression);
  }

  private void simpleName(final SyntaxNode node) {
    CSharpSyntax.name(node)
        .ifPresent(
            name ->
                walk.builder()
                    .reference(CSharpSyntax.text(name), name.span(), ReferenceAccess.READ)
        );
    walk.visitUnnamed(node);
  }

  /** A plain assignment writes its target without reading it; a compound one does both. */
  private void expression(final SyntaxNode node) {
    final Optional<SyntaxNode> target = assignedName(node);
    if (target.isEmpty()) {
      walk.generic(node);
      return;
    }
    final SyntaxNode name = target.orElseThrow();
    if (!"=".equals(CSharpSyntax.text(node.children().get(OPERATOR)))) {
      walk.visit(node.children().get(0));
    }
    walk.builder().reference(CSharpSyntax.text(name), name.span(), ReferenceAccess.WRITE);
    walk.visit(node.children().get(VALUE));
  }

  private static Optional<SyntaxNode> assignedName(final SyntaxNode node) {
    if (node.children().size() != ASSIGNMENT_PARTS) {
      return Optional.empty();
    }
    return CSharpSyntax.soleDescendant(node.children().get(0), SIMPLE_NAME)
        .flatMap(CSharpSyntax::name);
  }
}
