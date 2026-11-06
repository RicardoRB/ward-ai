package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.FormatSyntax;
import dev.ward.analysis.domain.format.FormatSyntaxTree;

/** Parses JavaScript for the formatting safety checks. */
public final class JavaScriptFormatSyntax implements FormatSyntax {

  @Override
  public FormatSyntaxTree parse(final String source) {
    return EcmaParsing.javaScript(source).toSyntaxTree();
  }
}
