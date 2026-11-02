package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;

/** Parses one source unit of a language into the shared syntax tree. */
@FunctionalInterface
public interface LanguageFrontend {

  /**
   * Parses the unit.
   *
   * @param unit the source unit to parse
   * @return the root node of the syntax tree
   */
  SyntaxNode parse(SourceUnit unit);
}
