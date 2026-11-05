package dev.ward.analysis.domain.flow;

import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.Objects;

/**
 * One variable access of a graph, identified by its block and its position in the block.
 *
 * @param block the index of the block that holds the access
 * @param index the position of the access in the block's accesses
 * @param access the access itself
 */
public record AccessSite(int block, int index, VariableAccess access) {

  /**
   * Validates the fields.
   *
   * @throws IllegalArgumentException if the block or index is negative
   * @throws NullPointerException if the access is missing
   */
  public AccessSite {
    if (block < 0 || index < 0) {
      throw new IllegalArgumentException("block and index must not be negative");
    }
    Objects.requireNonNull(access, "access");
  }
}
