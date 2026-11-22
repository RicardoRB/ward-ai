package dev.ward.analysis.frontend.antlr.format.csharp;

import dev.ward.analysis.domain.format.FormatSyntax;
import dev.ward.analysis.domain.format.FormatSyntaxTree;

/** Parses C# for the format safety checks: tokens, rule kinds and the first syntax error. */
public final class CSharpFormatSyntax implements FormatSyntax {
  @Override
  public FormatSyntaxTree parse(final String source) {
    final CSharpParse parse = CSharpParse.of(source);
    return new FormatSyntaxTree(parse.errorLine(), parse.formatTokens(), parse.rules());
  }
}
