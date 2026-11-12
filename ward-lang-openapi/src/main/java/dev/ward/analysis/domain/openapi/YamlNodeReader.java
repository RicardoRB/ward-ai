package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** Reads the node that starts at the cursor; the block collections call back into the parser. */
@FunctionalInterface
interface YamlNodeReader {

  /**
   * Reads a node.
   *
   * @param parentIndent the indentation of the collection the node is in, -1 at the root
   * @param allowBlock whether the node may be a block mapping or sequence starting on this line
   */
  SyntaxNode read(int parentIndent, boolean allowBlock);
}
