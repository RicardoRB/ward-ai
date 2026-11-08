package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/**
 * Reads the modifiers of a declaration. They sit on the wrapper of the declaration, a top-level
 * {@code typeDeclaration} or a member's {@code classBodyDeclaration}, so they are found from the
 * ancestors of the declaration.
 */
final class DeclaredModifiers {

  private static final Set<String> WRAPPERS = Set.of("typeDeclaration", "classBodyDeclaration");

  private DeclaredModifiers() {}

  /** Returns whether the declaration with these ancestors has the modifier. */
  static boolean has(final List<SyntaxNode> ancestors, final String modifier) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode ancestor = ancestors.get(index);
      if (WRAPPERS.contains(ancestor.kind())) {
        return ancestor.children().stream()
            .anyMatch(child -> modifier.equals(SyntaxWalk.text(child)));
      }
    }
    return false;
  }
}
