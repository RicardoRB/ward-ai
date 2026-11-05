package dev.ward.analysis.domain.flow;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;

/**
 * Identifies one local variable of a graph: its name and where it is declared. Two names that are
 * declared apart are different variables.
 *
 * @param name the name of the variable
 * @param declaration where the variable is declared
 */
record VariableKey(String name, Span declaration) {

  /** Returns the variable an access touches. */
  static VariableKey of(final VariableAccess access) {
    return new VariableKey(access.name(), access.declaration());
  }
}
