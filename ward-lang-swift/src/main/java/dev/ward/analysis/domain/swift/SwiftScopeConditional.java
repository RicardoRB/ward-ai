package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Handles {@code #if} blocks. A name declared in each branch is one name at run time, and a read
 * after the block reaches only the last declaration, so the earlier ones looked unused (#752).
 * Each declaration that a later branch declares again counts as read.
 */
final class SwiftScopeConditional {
  private static final Set<String> DECLARING = Set.of(
      "constant_declaration",
      "variable_declaration"
  );
  private static final Set<String> PASSING =
      Set.of(
          "statements",
          "statements_impl",
          "statement",
          "declaration",
          "pattern_initializer_list",
          "pattern_initializer",
          "pattern",
          "identifier_pattern"
      );
  private static final String IDENTIFIER = "declaration_identifier";

  private final SwiftScopeWalk walk;

  SwiftScopeConditional(final SwiftScopeWalk walk) {
    this.walk = walk;
  }

  void block(final SyntaxNode node) {
    walk.generic(node);
    final List<List<SyntaxNode>> branches =
        node.children().stream()
            .filter(child -> child.kind().endsWith("_directive_clause"))
            .map(SwiftScopeConditional::declaredIn)
            .toList();
    IntStream.range(0, branches.size() - 1).forEach(i -> readRedeclared(branches, i));
  }

  private static List<SyntaxNode> declaredIn(final SyntaxNode clause) {
    final List<SyntaxNode> names = new ArrayList<>();
    clause.children().forEach(child -> declared(child, false, names));
    return names;
  }

  /** Marks as read each name of branch {@code index} that a later branch declares again. */
  private void readRedeclared(final List<List<SyntaxNode>> branches, final int index) {
    final Set<String> later =
        branches.subList(index + 1, branches.size()).stream()
            .flatMap(List::stream)
            .map(SwiftScopeWalk::text)
            .collect(Collectors.toSet());
    branches.get(index).stream()
        .filter(name -> later.contains(SwiftScopeWalk.text(name)))
        .forEach(
            name ->
                walk.builder.reference(SwiftScopeWalk.text(name), name.span(), ReferenceAccess.READ)
        );
  }

  /** Collects the names a branch declares at its own level, not those of a nested block. */
  private static void declared(
      final SyntaxNode node,
      final boolean inDeclaration,
      final List<SyntaxNode> into
  ) {
    final boolean inside = inDeclaration || DECLARING.contains(node.kind());
    if (inside && IDENTIFIER.equals(node.kind())) {
      into.add(node);
    } else if (PASSING.contains(node.kind()) || DECLARING.contains(node.kind())) {
      node.children().forEach(child -> declared(child, inside, into));
    }
  }
}
