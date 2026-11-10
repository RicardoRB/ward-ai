package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.Map;

/** Counts the identifier tokens of a file, so a check can tell whether a name appears in it. */
public final class NodeIdentifiers {

  private static final String IDENTIFIER = "Identifier";

  private NodeIdentifiers() {}

  /**
   * Returns how many times each identifier appears in the file, a declaration included.
   *
   * @param tree the file's syntax tree
   * @return the count of each identifier name
   */
  public static Map<String, Integer> counts(final SyntaxNode tree) {
    final NodeTokens tokens = new NodeTokens(NodeLeaves.of(tree));
    final Map<String, Integer> counts = new HashMap<>();
    for (int i = 0; i < tokens.size(); i++) {
      if (IDENTIFIER.equals(tokens.kind(i))) {
        counts.merge(tokens.text(i), 1, Integer::sum);
      }
    }
    return Map.copyOf(counts);
  }
}
