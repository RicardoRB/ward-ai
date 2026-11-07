package dev.ward.analysis.frontend.antlr.format.java;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.frontend.antlr.java.JavaLexer;
import dev.ward.analysis.frontend.antlr.java.JavaParser;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.FailedPredicateException;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * One lexed and parsed Java source: every token including the hidden ones, the first syntax error
 * line when there is one, and the tree facts the formatter and the safety checks read.
 *
 * @param tokens every token of the source, hidden channels included, in order
 * @param errorLine the first line with a lexer or parser error, or empty when the source parses
 * @param facts the parse-tree rules and the token positions the layout needs
 */
record JavaFormatParse(List<Token> tokens, OptionalInt errorLine, JavaTreeFacts facts) {

  private static final String UNIT_PATH = "format";
  private static final int NO_LINE = Integer.MAX_VALUE;

  /**
   * Lexes and parses a source text.
   *
   * @param source the source text
   * @return the parse
   */
  static JavaFormatParse parse(final String source) {
    final JavaFormatParse unit = parse(source, JavaParser::compilationunit);
    if (unit.errorLine().isEmpty()) {
      return unit;
    }
    final JavaFormatParse compact = parse(source, JavaParser::compactCompilationUnit);
    return compact.errorLine().isEmpty() ? compact : unit;
  }

  private static JavaFormatParse parse(
      final String source, final Function<JavaParser, ParserRuleContext> start) {
    final JavaLexer lexer = new JavaLexer(CharStreams.fromString(source));
    lexer.removeErrorListeners();
    lexer.startFindings(UNIT_PATH);
    final CommonTokenStream stream = new CommonTokenStream(lexer);
    final JavaParser parser = new JavaParser(stream);
    parser.removeErrorListeners();
    final FirstErrorLine errors = new FirstErrorLine();
    parser.setErrorHandler(errors);
    final ParserRuleContext root = start.apply(parser);
    stream.fill();
    final int line = Math.min(lexerErrorLine(lexer), errors.line());
    final OptionalInt errorLine = line == NO_LINE ? OptionalInt.empty() : OptionalInt.of(line);
    final JavaTreeFacts facts = JavaTreeWalk.collect(root, parser.getRuleNames());
    return new JavaFormatParse(List.copyOf(stream.getTokens()), errorLine, facts);
  }

  private static int lexerErrorLine(final JavaLexer lexer) {
    int line = NO_LINE;
    for (final AnalysisFinding finding : lexer.findings()) {
      line = Math.min(line, finding.line());
    }
    return line;
  }

  /** Records the line of every parser error and recovers as the default strategy does. */
  private static final class FirstErrorLine extends DefaultErrorStrategy {

    private int first = NO_LINE;

    @Override
    protected void reportNoViableAlternative(
        final Parser recognizer, final NoViableAltException e) {
      record(e.getOffendingToken());
      super.reportNoViableAlternative(recognizer, e);
    }

    @Override
    protected void reportInputMismatch(final Parser recognizer, final InputMismatchException e) {
      record(e.getOffendingToken());
      super.reportInputMismatch(recognizer, e);
    }

    @Override
    protected void reportFailedPredicate(
        final Parser recognizer, final FailedPredicateException e) {
      record(e.getOffendingToken());
      super.reportFailedPredicate(recognizer, e);
    }

    @Override
    protected void reportUnwantedToken(final Parser recognizer) {
      record(recognizer.getCurrentToken());
      super.reportUnwantedToken(recognizer);
    }

    @Override
    protected void reportMissingToken(final Parser recognizer) {
      record(recognizer.getCurrentToken());
      super.reportMissingToken(recognizer);
    }

    int line() {
      return first;
    }

    private void record(final Token offending) {
      if (offending != null) {
        first = Math.min(first, offending.getLine());
      }
    }
  }
}
