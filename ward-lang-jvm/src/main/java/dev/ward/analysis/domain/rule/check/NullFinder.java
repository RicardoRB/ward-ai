package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Finds where one nullness rule reports in a file. */
@FunctionalInterface
public interface NullFinder {

  /**
   * The file as the finders read it.
   *
   * @param root the syntax tree
   * @param types the types of the file
   */
  record Unit(SyntaxNode root, List<NullModel.Type> types) {

    /** Copies the types. */
    public Unit {
      types = List.copyOf(types);
    }
  }

  /** Returns the hits in {@code unit}, in source order. */
  List<NullHit> find(Unit unit);
}
