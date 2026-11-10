package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/**
 * Whether a source is a module or a script. A source with an import or export declaration is a
 * module; any other is a script, where the top-level declarations are global declarations. ESLint
 * reads the source type from the configuration; Ward reads it from the source.
 */
final class SourceKind {

  private static final Set<String> MODULE_STATEMENTS = Set.of("importStatement", "exportStatement");

  private SourceKind() {}

  /** Whether the tree holds an import or export declaration. */
  static boolean isModule(final SyntaxNode root) {
    return anyDescendant(root);
  }

  private static boolean anyDescendant(final SyntaxNode node) {
    if (MODULE_STATEMENTS.contains(node.kind())) {
      return true;
    }
    final List<SyntaxNode> children = node.children();
    return children.stream().anyMatch(SourceKind::anyDescendant);
  }
}
