package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.csharp.CSharpLexer;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/** The sections a false C# conditional branch hides from the parser, as {@code skipped} nodes. */
final class SkippedSections {
  /** The kind of the node, and the field that holds its text. */
  static final String KIND = "skipped";

  private SkippedSections() {}

  /** Whether {@code token} of {@code tokens} is the text of a false branch with something in it. */
  static boolean is(final TokenStream tokens, final Token token) {
    return tokens.getTokenSource() instanceof CSharpLexer
        && token.getType() == CSharpLexer.SKIPPED_SECTION
        && !token.getText().isBlank();
  }

  /** The text is under {@code skipped}, not {@code text}, so no rule reads it as a token. */
  static SyntaxNode node(final Token token) {
    return new SyntaxNode(
        KIND,
        SyntaxSpans.comment(token),
        Map.of(KIND, token.getText()),
        List.of()
    );
  }
}
