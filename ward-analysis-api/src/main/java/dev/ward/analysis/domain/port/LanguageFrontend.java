package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.SourceUnit;

/** Parses one source unit of a language into the shared syntax tree. */
@FunctionalInterface
public interface LanguageFrontend {

  /**
   * Parses the unit. Syntax errors are returned as diagnostics next to a partial tree.
   *
   * @param unit the source unit to parse
   * @return the syntax tree and its diagnostics
   */
  ParseResult parse(SourceUnit unit);
}
