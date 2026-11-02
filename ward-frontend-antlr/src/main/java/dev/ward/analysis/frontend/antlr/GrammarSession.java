package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Parses units of one language with one lexer and one parser, reset for each unit. Not thread-safe:
 * use one session per thread.
 *
 * @param <L> the generated lexer type
 * @param <P> the generated parser type
 */
final class GrammarSession<L extends WardLexerBase, P extends Parser> implements LanguageFrontend {

  private static final Logger LOG = Logger.getLogger(GrammarSession.class.getName());

  private final L lexer;
  private final P parser;
  private final Function<P, ParserRuleContext> startRule;
  private final SyntaxFindings findings = new SyntaxFindings();

  /**
   * Binds the generated lexer and parser to the findings of this session.
   *
   * @param lexer the generated lexer
   * @param parser the generated parser, reading the lexer's tokens
   * @param startRule the grammar's entry rule
   */
  GrammarSession(final L lexer, final P parser, final Function<P, ParserRuleContext> startRule) {
    this.lexer = lexer;
    this.parser = parser;
    this.startRule = startRule;
    this.lexer.removeErrorListeners();
    this.parser.removeErrorListeners();
    this.parser.setErrorHandler(new WardErrorStrategy(findings));
  }

  @Override
  public ParseResult parse(final SourceUnit unit) {
    findings.start(unit.path());
    lexer.startFindings(unit.path());
    lexer.setInputStream(CharStreams.fromString(unit.text()));
    parser.setInputStream(new CommonTokenStream(lexer));
    LOG.fine(() -> "parse started: " + parser.getClass().getSimpleName());
    final ParserRuleContext root = startRule.apply(parser);
    final List<AnalysisFinding> diagnostics = new ArrayList<>(lexer.findings());
    diagnostics.addAll(findings.findings());
    diagnostics.sort(
        Comparator.comparingInt(AnalysisFinding::line).thenComparingInt(AnalysisFinding::column));
    final ParseResult result = new ParseResult(SyntaxTreeMapper.map(root, parser), diagnostics);
    LOG.fine(
        () ->
            "parse finished: "
                + parser.getClass().getSimpleName()
                + " diagnostics="
                + result.diagnostics().size());
    return result;
  }
}
