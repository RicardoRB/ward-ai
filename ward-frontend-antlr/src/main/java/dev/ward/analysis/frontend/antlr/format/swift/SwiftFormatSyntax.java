package dev.ward.analysis.frontend.antlr.format.swift;

import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.domain.format.FormatSyntax;
import dev.ward.analysis.domain.format.FormatSyntaxTree;
import dev.ward.analysis.domain.format.FormatToken;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.frontend.antlr.swift.SwiftLexer;
import dev.ward.analysis.frontend.antlr.swift.SwiftParser;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;

/**
 * The Swift grammar as the formatter's safety checks see it: tokens, rule kinds and syntax errors.
 */
public final class SwiftFormatSyntax implements FormatSyntax {

  /** Marks that no syntax error has been seen yet. */
  private static final int NO_LINE = -1;

  /** Parses the source from its first rule and reports the tokens and rule kinds in pre-order. */
  @Override
  public FormatSyntaxTree parse(final String source) {
    final SwiftLexer lexer = SwiftLexing.lexer(source);
    final CommonTokenStream stream = SwiftLexing.filled(lexer);
    final SwiftParser parser = new SwiftParser(stream);
    parser.removeErrorListeners();
    final FirstSyntaxError errors = new FirstSyntaxError();
    parser.setErrorHandler(errors);
    final ParserRuleContext root = parser.top_level();
    final List<FormatRule> rules = new ArrayList<>();
    collectRules(root, parser.getRuleNames(), rules);
    final List<FormatToken> tokens =
        SwiftLexing.formatTokens(SwiftLexing.significant(stream.getTokens()));
    return new FormatSyntaxTree(firstLine(lexer, errors), tokens, rules);
  }

  private static OptionalInt firstLine(final SwiftLexer lexer, final FirstSyntaxError errors) {
    int first = errors.line();
    for (final AnalysisFinding finding : lexer.findings()) {
      if (first == NO_LINE || finding.line() < first) {
        first = finding.line();
      }
    }
    return first == NO_LINE ? OptionalInt.empty() : OptionalInt.of(first);
  }

  private static void collectRules(
      final ParserRuleContext context, final String[] names, final List<FormatRule> rules) {
    rules.add(new FormatRule(names[context.getRuleIndex()], context.getStart().getLine()));
    for (int i = 0; i < context.getChildCount(); i++) {
      if (context.getChild(i) instanceof ParserRuleContext nested) {
        collectRules(nested, names, rules);
      }
    }
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
