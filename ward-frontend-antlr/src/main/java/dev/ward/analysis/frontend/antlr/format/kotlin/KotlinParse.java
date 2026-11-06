package dev.ward.analysis.frontend.antlr.format.kotlin;

import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.domain.format.FormatToken;
import dev.ward.analysis.frontend.antlr.kotlin.KotlinLexer;
import dev.ward.analysis.frontend.antlr.kotlin.KotlinParser;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;

/**
 * One parse of a Kotlin source for the formatter: the significant tokens and comments in source
 * order, the parse tree's facts, and the first syntax error.
 */
final class KotlinParse {

  private static final int FIRST_LINE = 1;
  private static final String LEXER_UNIT = "kotlin-format";

  private final int[] codePoints;
  private final List<Token> items;
  private final KotlinTreeWalk walk;
  private final OptionalInt errorLine;

  private KotlinParse(
      final int[] codePoints,
      final List<Token> items,
      final KotlinTreeWalk walk,
      final OptionalInt errorLine) {
    this.codePoints = codePoints;
    this.items = items;
    this.walk = walk;
    this.errorLine = errorLine;
  }

  /**
   * Parses a source as a Kotlin file, or as a script when the file rule fails and the script rule
   * accepts it.
   *
   * @param source the source text
   * @return the parse
   */
  static KotlinParse of(final String source) {
    final KotlinParse file = parse(source, KotlinParser::kotlinFile);
    if (file.errorLine().isEmpty()) {
      return file;
    }
    final KotlinParse script = parse(source, KotlinParser::script);
    return script.errorLine().isPresent() ? file : script;
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
  KotlinTreeWalk walk() {
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
   * Returns the tokens for the format checks: every significant token, with comments typed as
   * {@link KotlinTokens#COMMENT}.
   *
   * @return the tokens
   */
  List<FormatToken> formatTokens() {
    final List<FormatToken> tokens = new ArrayList<>(items.size());
    for (final Token token : items) {
      final String type =
          KotlinTokens.isComment(token)
              ? KotlinTokens.COMMENT
              : KotlinLexer.VOCABULARY.getDisplayName(token.getType());
      tokens.add(new FormatToken(type, token.getText(), token.getLine()));
    }
    return tokens;
  }

  private static KotlinParse parse(
      final String source, final Function<KotlinParser, ParserRuleContext> start) {
    final ErrorLines errors = new ErrorLines();
    final KotlinLexer lexer = new KotlinLexer(CharStreams.fromString(source));
    lexer.startFindings(LEXER_UNIT);
    final CommonTokenStream stream = new CommonTokenStream(lexer);
    final KotlinParser parser =
        new KotlinParser(stream) {
          @Override
          public void notifyErrorListeners(
              final Token offending, final String message, final RecognitionException error) {
            errors.record(offending == null ? FIRST_LINE : offending.getLine());
          }
        };
    final ParserRuleContext root = start.apply(parser);
    stream.fill();
    lexer.findings().forEach(finding -> errors.record(finding.line()));
    return new KotlinParse(
        source.codePoints().toArray(),
        significant(stream.getTokens()),
        new KotlinTreeWalk(root),
        errors.first());
  }

  private static List<Token> significant(final List<Token> all) {
    final List<Token> items = new ArrayList<>();
    for (final Token token : all) {
      if (token.getType() != Token.EOF && token.getType() != KotlinLexer.NL) {
        items.add(token);
      }
    }
    return items;
  }

  /** Keeps the earliest line that a lexer or parser reported as an error. */
  private static final class ErrorLines {
    private OptionalInt first = OptionalInt.empty();

    void record(final int line) {
      if (first.isEmpty() || line < first.getAsInt()) {
        first = OptionalInt.of(line);
      }
    }

    OptionalInt first() {
      return first;
    }
  }
}
