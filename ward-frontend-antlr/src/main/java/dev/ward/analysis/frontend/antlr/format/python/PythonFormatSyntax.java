package dev.ward.analysis.frontend.antlr.format.python;

import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.domain.format.FormatSyntax;
import dev.ward.analysis.domain.format.FormatSyntaxTree;
import dev.ward.analysis.domain.format.FormatToken;
import dev.ward.analysis.frontend.antlr.python.Python3Lexer;
import dev.ward.analysis.frontend.antlr.python.Python3Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Parses Python source for the formatter's safety checks. Its tokens are the lexer's tokens with
 * comments and the structural markers; a line break is always {@code \n}, so only the layout
 * differs between a source and its formatted text.
 */
public final class PythonFormatSyntax implements FormatSyntax {
  private static final String NEWLINE_TYPE = "NEWLINE";
  private static final String NEWLINE_TEXT = "\n";
  private static final int FIRST_LINE = 1;

  @Override
  public FormatSyntaxTree parse(final String source) {
    final Python3Lexer lexer = PythonEntries.lexer(source);
    final CommonTokenStream stream = new CommonTokenStream(lexer);
    final Python3Parser parser = new Python3Parser(stream);
    final FirstError errors = new FirstError();
    parser.removeErrorListeners();
    parser.setErrorHandler(errors);
    final ParserRuleContext root = parser.file_input();
    stream.fill();
    final List<Token> tokens = stream.getTokens();
    final OptionalInt errorLine = errors.line().isPresent() ? errors.line() : unknownLine(tokens);
    return new FormatSyntaxTree(
        errorLine,
        formatTokens(PythonEntries.of(source, tokens)),
        rules(root)
    );
  }

  private static OptionalInt unknownLine(final List<Token> tokens) {
    return tokens.stream()
        .filter(token -> token.getType() == Python3Lexer.UNKNOWN_CHAR)
        .mapToInt(Token::getLine)
        .findFirst();
  }

  private static List<FormatToken> formatTokens(final List<PythonEntry> entries) {
    final int trailing = trailingRun(entries);
    final List<FormatToken> tokens = new ArrayList<>();
    for (int index = 0; index < entries.size(); index++) {
      final PythonEntry entry = entries.get(index);
      if (index < trailing || !isLineBreak(entry)) {
        tokens.add(new FormatToken(entry.typeName(), entry.text(), entry.line()));
      }
    }
    return tokens;
  }

  /**
   * Finds where the line breaks and dedents that end the source start. The lexer emits one line
   * break when the text ends with a line break and one more for each blank line after it, so the
   * line breaks of that run are left out, and a source has the same tokens with or without a final
   * line break or blank lines at its end.
   */
  private static int trailingRun(final List<PythonEntry> entries) {
    int index = entries.size();
    while (index > 0
        && (entries.get(index - 1).kind() == PythonEntry.Kind.DEDENT
            || isLineBreak(entries.get(index - 1)))) {
      index--;
    }
    return index;
  }

  private static boolean isLineBreak(final PythonEntry entry) {
    return entry.kind() == PythonEntry.Kind.NEWLINE
        && NEWLINE_TYPE.equals(entry.typeName())
        && NEWLINE_TEXT.equals(entry.text());
  }

  private static List<FormatRule> rules(final ParseTree root) {
    final List<FormatRule> rules = new ArrayList<>();
    collect(root, rules);
    return rules;
  }

  private static void collect(final ParseTree node, final List<FormatRule> rules) {
    if (node instanceof ParserRuleContext context) {
      final Token start = context.getStart();
      final int line = start == null ? FIRST_LINE : start.getLine();
      rules.add(new FormatRule(Python3Parser.ruleNames[context.getRuleIndex()], line));
      for (int index = 0; index < context.getChildCount(); index++) {
        collect(context.getChild(index), rules);
      }
    }
  }

  /** Keeps the line of the first syntax error the parser reports. */
  private static final class FirstError extends DefaultErrorStrategy {
    private OptionalInt line = OptionalInt.empty();

    @Override
    public void reportError(final Parser recognizer, final RecognitionException error) {
      record(recognizer);
    }

    @Override
    protected void reportMissingToken(final Parser recognizer) {
      record(recognizer);
    }

    @Override
    protected void reportUnwantedToken(final Parser recognizer) {
      record(recognizer);
    }

    OptionalInt line() {
      return line;
    }

    private void record(final Parser recognizer) {
      if (line.isEmpty()) {
        line = OptionalInt.of(recognizer.getCurrentToken().getLine());
      }
    }
  }
}
