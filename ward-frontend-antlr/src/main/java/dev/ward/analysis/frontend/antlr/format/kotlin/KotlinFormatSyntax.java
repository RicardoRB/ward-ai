package dev.ward.analysis.frontend.antlr.format.kotlin;

import dev.ward.analysis.domain.format.FormatSyntax;
import dev.ward.analysis.domain.format.FormatSyntaxTree;

/** Parses Kotlin for the format safety checks: tokens, rule kinds and the first syntax error. */
public final class KotlinFormatSyntax implements FormatSyntax {

  @Override
  public FormatSyntaxTree parse(final String source) {
    final KotlinParse parse = KotlinParse.of(source);
    return new FormatSyntaxTree(parse.errorLine(), parse.formatTokens(), parse.rules());
  }
}
