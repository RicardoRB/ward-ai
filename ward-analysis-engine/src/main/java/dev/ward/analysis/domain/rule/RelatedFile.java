package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Objects;

/**
 * A file that a unit depends on, with its syntax tree, for a rule that reads the values another
 * file declares.
 *
 * @param unit the file
 * @param tree the syntax tree of the file
 */
public record RelatedFile(SourceUnit unit, SyntaxNode tree) {
  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public RelatedFile {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(tree, "tree");
  }
}
