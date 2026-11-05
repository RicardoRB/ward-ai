package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;

/** Turns a parsed source unit into one control-flow graph per executable body. */
@FunctionalInterface
public interface ControlFlowLowering {

  /**
   * Lowers every body of the unit. A body whose constructs cannot be lowered is reported as a
   * failure and left out; the other bodies are still lowered.
   *
   * @param unit the source unit the tree was parsed from
   * @param tree the syntax tree of the unit
   * @return the graphs and the failures
   */
  ControlFlowResult lower(SourceUnit unit, SyntaxNode tree);
}
