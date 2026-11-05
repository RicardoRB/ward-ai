package dev.ward.analysis.domain.format;

/**
 * Parses source text for the formatting safety checks. A language adapter implements it with its
 * parser, so the engine never depends on a grammar.
 */
@FunctionalInterface
public interface FormatSyntax {

  /**
   * Parses a source text.
   *
   * @param source the source text
   * @return the syntax view of the text
   */
  FormatSyntaxTree parse(String source);
}
