package dev.ward.analysis.frontend.antlr.format.java;

import dev.ward.analysis.domain.format.FormatSyntax;
import dev.ward.analysis.domain.format.FormatSyntaxTree;
import dev.ward.analysis.domain.format.FormatToken;
import dev.ward.analysis.frontend.antlr.java.JavaLexer;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The Java parser view the formatter's safety checks read: the non-whitespace tokens with comments
 * typed {@code comment}, the parse-tree rule kinds in pre-order, and the first syntax error line.
 */
public final class JavaFormatSyntax implements FormatSyntax {

  private static final String COMMENT_TYPE = "comment";

  @Override
  public FormatSyntaxTree parse(final String source) {
    final JavaFormatParse parsed = JavaFormatParse.parse(source);
    final List<FormatToken> tokens = new ArrayList<>();
    for (final Token token : parsed.tokens()) {
      final int type = token.getType();
      if (type != Token.EOF && type != JavaLexer.WS) {
        tokens.add(new FormatToken(typeName(type), token.getText(), token.getLine()));
      }
    }
    return new FormatSyntaxTree(parsed.errorLine(), tokens, parsed.facts().rules());
  }

  private static String typeName(final int type) {
    if (type == JavaLexer.COMMENT || type == JavaLexer.LINE_COMMENT) {
      return COMMENT_TYPE;
    }
    return JavaLexer.VOCABULARY.getDisplayName(type);
  }
}
