package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.frontend.antlr.javascript.JavaScriptLexer;
import dev.ward.analysis.frontend.antlr.javascript.JavaScriptParser;
import dev.ward.analysis.frontend.antlr.typescript.TypeScriptLexer;
import dev.ward.analysis.frontend.antlr.typescript.TypeScriptParser;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Parses TypeScript and JavaScript sources for the formatters, with a new lexer and parser each
 * time.
 */
final class EcmaParsing {

  private EcmaParsing() {}

  /**
   * Parses a TypeScript source.
   *
   * @param source the source text
   * @return the parse
   */
  static EcmaParse typeScript(final String source) {
    final TypeScriptLexer lexer = new TypeScriptLexer(CharStreams.fromString(source));
    final TypeScriptParser parser = new TypeScriptParser(new CommonTokenStream(lexer));
    return parse(lexer, parser, TypeScriptParser::program);
  }

  /**
   * Parses a JavaScript source.
   *
   * @param source the source text
   * @return the parse
   */
  static EcmaParse javaScript(final String source) {
    final JavaScriptLexer lexer = new JavaScriptLexer(CharStreams.fromString(source));
    final JavaScriptParser parser = new JavaScriptParser(new CommonTokenStream(lexer));
    return parse(lexer, parser, JavaScriptParser::program);
  }

  private static <P extends Parser> EcmaParse parse(
      final Lexer lexer, final P parser, final Function<P, ParserRuleContext> start) {
    final FirstErrorLine errors = new FirstErrorLine();
    lexer.removeErrorListeners();
    parser.removeErrorListeners();
    parser.setErrorHandler(errors);
    final ParserRuleContext tree = start.apply(parser);
    final CommonTokenStream stream = (CommonTokenStream) parser.getTokenStream();
    stream.fill();
    final EcmaTokenCollector collector = new EcmaTokenCollector(lexer.getVocabulary());
    for (final Token token : stream.getTokens()) {
      collector.accept(token);
    }
    final List<FormatRule> rules = new ArrayList<>();
    collectRules(tree, parser.getRuleNames(), rules);
    return collector.build(rules, errors.first());
  }

  private static void collectRules(
      final ParseTree node, final String[] ruleNames, final List<FormatRule> out) {
    if (!(node instanceof ParserRuleContext rule)) {
      return;
    }
    final int line = rule.getStart() == null ? 1 : rule.getStart().getLine();
    out.add(new FormatRule(ruleNames[rule.getRuleIndex()], line));
    for (int i = 0; i < rule.getChildCount(); i++) {
      collectRules(rule.getChild(i), ruleNames, out);
    }
  }

  /**
   * Keeps the earliest syntax error line the parser reports. The parser reports through its error
   * strategy, so the listener interface, whose callback takes six parameters, is not needed.
   */
  private static final class FirstErrorLine extends DefaultErrorStrategy {

    private int line = Integer.MAX_VALUE;

    @Override
    public void reportError(final Parser recognizer, final RecognitionException error) {
      final Token offending = error == null ? null : error.getOffendingToken();
      final Token token = offending == null ? recognizer.getCurrentToken() : offending;
      line = Math.min(line, token.getLine());
      super.reportError(recognizer, error);
    }

    OptionalInt first() {
      return line == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(line);
    }
  }
}
