package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the module references and the exported names of one file from its tokens. The token order
 * is used rather than the tree shape, because the grammar's import and export rules do not cover
 * every form (type-only imports, export lists) and the tokens are exact for every form.
 */
final class NodeModuleScan {
  private static final String OPEN_BRACE = "{";
  private static final String CLOSE_BRACE = "}";

  private final NodeTokens tokens;
  private final NodeExportScan exports;
  private final NodeReferenceScan references;

  /** Creates the scan of the tokens under {@code tree}. */
  NodeModuleScan(final SyntaxNode tree) {
    this.tokens = new NodeTokens(NodeLeaves.of(tree));
    this.exports = new NodeExportScan(tokens);
    this.exports.exportLists(tree);
    this.references = new NodeReferenceScan(tokens, exports);
  }

  /** Scans every token once, in source order, tracking brace depth for top-level declarations. */
  void run() {
    int depth = 0;
    for (int i = 0; i < tokens.size(); i++) {
      if (tokens.isPunctuation(i, OPEN_BRACE)) {
        depth++;
      } else if (tokens.isPunctuation(i, CLOSE_BRACE)) {
        depth--;
      }
      references.token(i);
      exports.token(i, depth);
    }
  }

  List<Reference> references() {
    return references.references();
  }

  List<Symbol> exports() {
    return exports.exports();
  }

  /** Returns the exported names and the top-level declarations, without repeats. */
  List<Symbol> declared() {
    final Set<Symbol> names = new LinkedHashSet<>(exports.exports());
    names.addAll(exports.declared());
    return List.copyOf(names);
  }
}
