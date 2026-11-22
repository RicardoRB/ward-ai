package dev.ward.analysis.frontend.antlr.format.csharp;

import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.domain.format.FormatToken;
import dev.ward.analysis.frontend.antlr.csharp.CSharpLexer;
import dev.ward.analysis.frontend.antlr.csharp.CSharpParser;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;

/**
 * One parse of a C# source for the formatter: the significant tokens and comments in source order,
 * the parse tree's facts, and the first syntax error.
 */
final class CSharpParse {
  /** The token type given to every comment, as the format checks expect. */
  static final String COMMENT = "comment";

  private static final int FIRST_LINE = 1;
  private static final String LEXER_UNIT = "csharp-format";

  private final int[] codePoints;
  private final List<Token> items;
  private final CSharpTreeWalk walk;
  private final OptionalInt errorLine;

  private CSharpParse(
      final int[] codePoints,
      final List<Token> items,
      final CSharpTreeWalk walk,
      final OptionalInt errorLine
  ) {
    this.codePoints = codePoints;
    this.items = items;
    this.walk = walk;
    this.errorLine = errorLine;
  }

  /**
   * Parses a source as a C# compilation unit.
   *
   * @param source the source text
   * @return the parse
   */
  static CSharpParse of(final String source) {
    final int[] errors = {Integer.MAX_VALUE};
    final CSharpLexer lexer = new CSharpLexer(CharStreams.fromString(source));
    lexer.startFindings(LEXER_UNIT);
    final CommonTokenStream stream = new CommonTokenStream(lexer);
    final CSharpParser parser =
        new CSharpParser(stream) {
          @Override
          public void notifyErrorListeners(
              final Token offending,
              final String message,
              final RecognitionException error
          ) {
            errors[0] = Math.min(errors[0], offending == null ? FIRST_LINE : offending.getLine());
          }
        };
    final ParserRuleContext root = parser.prog();
    stream.fill();
    lexer.findings().forEach(finding -> errors[0] = Math.min(errors[0], finding.line()));
    return new CSharpParse(
        source.codePoints().toArray(),
        significant(stream.getTokens()),
        new CSharpTreeWalk(root, stream.size()),
        errors[0] == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(errors[0])
    );
  }

  /**
   * Returns the source code points, which the token indexes refer to.
   *
   * @return a copy of the code points
   */
  int[] codePoints() {
    return codePoints.clone();
  }

  /**
   * Returns the significant tokens and comments in source order.
   *
   * @return the tokens
   */
  List<Token> items() {
    return items;
  }

  /**
   * Returns the facts collected from the parse tree.
   *
   * @return the walk
   */
  CSharpTreeWalk walk() {
    return walk;
  }

  /**
   * Returns the first line with a syntax error.
   *
   * @return the line, when the source has a syntax error
   */
  OptionalInt errorLine() {
    return errorLine;
  }

  /**
   * Returns the rule kinds in pre-order.
   *
   * @return the rules
   */
  List<FormatRule> rules() {
    return walk.rules();
  }

  /**
   * Returns the line of the first section that a false conditional branch hides from the parser.
   *
   * @return the line, when the source has such a section
   */
  OptionalInt skippedLine() {
    return items.stream()
        .filter(token -> token.getType() == CSharpLexer.SKIPPED_SECTION)
        .mapToInt(Token::getLine)
        .findFirst();
  }

  /**
   * Returns the tokens for the format checks: every significant token, with comments typed as
   * {@link #COMMENT}.
   *
   * @return the tokens
   */
  List<FormatToken> formatTokens() {
    final List<FormatToken> tokens = new ArrayList<>(items.size());
    for (final Token token : items) {
      final String type =
          CSharpTokens.isComment(token)
              ? COMMENT
              : CSharpLexer.VOCABULARY.getDisplayName(token.getType());
      tokens.add(new FormatToken(type, CSharpTokens.text(codePoints, token), token.getLine()));
    }
    return tokens;
  }

  private static List<Token> significant(final List<Token> all) {
    final List<Token> items = new ArrayList<>();
    for (final Token token : all) {
      final boolean directive = token.getChannel() == CSharpLexer.DIRECTIVE;
      if (token.getType() != Token.EOF && !directive) {
        items.add(token);
      }
    }
    return items;
  }
}
