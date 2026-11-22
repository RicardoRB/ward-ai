package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.domain.format.FormatToken;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.frontend.antlr.rust.RustLexer;
import dev.ward.analysis.frontend.antlr.rust.RustParser;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;

/**
 * One parse of a Rust source for the formatter: the significant tokens, the tree and the first
 * syntax error. Comments are tokens of the hidden channel and are kept; whitespace is dropped.
 */
final class RustParsing {
  /** Marks that no syntax error has been seen yet. */
  private static final int NO_LINE = -1;

  /** Unit path given to the lexer's findings; the formatter never reports them by path. */
  private static final String UNIT = "format";

  /** Token type that the formatter reports for every comment. */
  private static final String COMMENT = "comment";

  private final List<Token> tokens;
  private final ParserRuleContext root;
  private final String[] ruleNames;
  private final OptionalInt errorLine;

  private RustParsing(
      final List<Token> tokens,
      final ParserRuleContext root,
      final String[] ruleNames,
      final OptionalInt errorLine
  ) {
    this.tokens = List.copyOf(tokens);
    this.root = root;
    this.ruleNames = ruleNames.clone();
    this.errorLine = errorLine;
  }

  /**
   * Parses a source from the grammar's first rule.
   *
   * @param source the Rust source
   * @return the parse
   */
  static RustParsing of(final String source) {
    final RustLexer lexer = new RustLexer(CharStreams.fromString(source));
    lexer.startFindings(UNIT);
    lexer.removeErrorListeners();
    final CommonTokenStream stream = new CommonTokenStream(lexer);
    stream.fill();
    final RustParser parser = new RustParser(stream);
    parser.removeErrorListeners();
    final FirstSyntaxError errors = new FirstSyntaxError();
    parser.setErrorHandler(errors);
    final ParserRuleContext root = parser.crate();
    return new RustParsing(
        significant(stream.getTokens()),
        root,
        parser.getRuleNames(),
        firstLine(lexer, errors)
    );
  }

  /**
   * The tokens that carry text to place: code and comments.
   *
   * @return the tokens in source order
   */
  List<Token> tokens() {
    return tokens;
  }

  /**
   * The root of the parse tree.
   *
   * @return the root rule context
   */
  ParserRuleContext root() {
    return root;
  }

  /**
   * The name of every grammar rule, by rule index.
   *
   * @return the rule names
   */
  String[] ruleNames() {
    return ruleNames.clone();
  }

  /**
   * The first line with a lexer or parser error.
   *
   * @return the line, or empty when the source is valid
   */
  OptionalInt errorLine() {
    return errorLine;
  }

  /**
   * Converts the tokens to the core's token records.
   *
   * @return one record per token, with comments typed {@code comment}
   */
  List<FormatToken> formatTokens() {
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
    return RustLexer.VOCABULARY.getDisplayName(token.getType());
  }

  private static List<Token> significant(final List<Token> all) {
    final List<Token> kept = new ArrayList<>();
    for (final Token token : all) {
      final int type = token.getType();
      if (type != RustLexer.WHITESPACE && type != RustLexer.NEWLINE && type != Token.EOF) {
        kept.add(token);
      }
    }
    return kept;
  }

  private static OptionalInt firstLine(final RustLexer lexer, final FirstSyntaxError errors) {
    int first = errors.line();
    for (final AnalysisFinding finding : lexer.findings()) {
      if (first == NO_LINE || finding.line() < first) {
        first = finding.line();
      }
    }
    return first == NO_LINE ? OptionalInt.empty() : OptionalInt.of(first);
  }

  /**
   * Keeps the earliest syntax error line the parser reports. The default recovery runs as usual;
   * only the reporting is replaced, so no message reaches the console.
   */
  private static final class FirstSyntaxError extends DefaultErrorStrategy {
    private int first = NO_LINE;

    @Override
    public void reportError(final Parser recognizer, final RecognitionException error) {
      final Token offending = error == null ? null : error.getOffendingToken();
      final int line =
          offending == null ? recognizer.getCurrentToken().getLine() : offending.getLine();
      if (first == NO_LINE || line < first) {
        first = line;
      }
      super.reportError(recognizer, error);
    }

    int line() {
      return first;
    }
  }
}
