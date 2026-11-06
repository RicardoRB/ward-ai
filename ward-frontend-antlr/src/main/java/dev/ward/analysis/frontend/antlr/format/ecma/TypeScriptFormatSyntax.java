package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.FormatSyntax;
import dev.ward.analysis.domain.format.FormatSyntaxTree;

/** Parses TypeScript for the formatting safety checks. */
public final class TypeScriptFormatSyntax implements FormatSyntax {

  @Override
  public FormatSyntaxTree parse(final String source) {
    return EcmaParsing.typeScript(source).toSyntaxTree();
  }
}
