package dev.ward.analysis.frontend.antlr.format.swift;

import dev.ward.analysis.domain.format.FormatToken;
import dev.ward.analysis.frontend.antlr.swift.SwiftLexer;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/** Lexes Swift source for the formatter; comments are typed {@code comment} as the core expects. */
final class SwiftLexing {

  /** Unit path given to the lexer's findings; the formatter never reports them by path. */
  private static final String UNIT = "format";

  /** Token type that the formatter reports for every comment. */
  private static final String COMMENT = "comment";

  private SwiftLexing() {}

  /**
   * Creates a lexer for the source whose lexer errors are kept as findings.
   *
   * @param source the Swift source
   * @return a lexer ready to fill a token stream
   */
  static SwiftLexer lexer(final String source) {
    final SwiftLexer lexer = new SwiftLexer(CharStreams.fromString(source));
    lexer.startFindings(UNIT);
    lexer.removeErrorListeners();
    return lexer;
  }

  /**
   * Fills a token stream from the lexer, so every token, hidden channel included, is buffered.
   *
   * @param lexer the lexer to read
   * @return the filled stream
   */
  static CommonTokenStream filled(final SwiftLexer lexer) {
    final CommonTokenStream stream = new CommonTokenStream(lexer);
    stream.fill();
    return stream;
  }

  /**
   * Keeps the tokens that carry text the formatter must place: code and comments, without
   * whitespace and the end-of-file token.
   *
   * @param all every token of the stream
   * @return the significant tokens in source order
   */
  static List<Token> significant(final List<Token> all) {
    final List<Token> kept = new ArrayList<>();
    for (final Token token : all) {
      if (token.getType() != SwiftLexer.WS && token.getType() != Token.EOF) {
        kept.add(token);
      }
    }
    return kept;
  }

  /**
   * Converts significant tokens to the core's token records.
   *
   * @param tokens the significant tokens
   * @return one record per token, with comments typed {@code comment}
   */
  static List<FormatToken> formatTokens(final List<Token> tokens) {
    final List<FormatToken> result = new ArrayList<>();
    for (final Token token : tokens) {
      result.add(new FormatToken(typeOf(token), token.getText(), token.getLine()));
    }
    return result;
  }

  private static String typeOf(final Token token) {
    if (token.getChannel() != Token.DEFAULT_CHANNEL) {
      return COMMENT;
    }
    return SwiftLexer.VOCABULARY.getDisplayName(token.getType());
  }
}
