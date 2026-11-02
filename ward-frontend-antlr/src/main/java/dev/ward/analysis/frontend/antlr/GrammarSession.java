package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.function.Function;
import java.util.logging.Logger;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Parses units of one language with one lexer and one parser, reset for each unit. Not thread-safe:
 * use one session per thread.
 *
 * @param <L> the generated lexer type
 * @param <P> the generated parser type
 */
final class GrammarSession<L extends Lexer, P extends Parser> implements LanguageFrontend {

  private static final Logger LOG = Logger.getLogger(GrammarSession.class.getName());

  private final L lexer;
  private final P parser;
  private final Function<P, ParserRuleContext> startRule;
  private final SyntaxErrorCollector errors = new SyntaxErrorCollector();

  /**
   * Binds the generated lexer and parser to the error collector.
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
    this.lexer.addErrorListener(errors);
    this.parser.removeErrorListeners();
    this.parser.addErrorListener(errors);
  }

  @Override
  public ParseResult parse(final SourceUnit unit) {
    errors.start(unit.path());
    lexer.setInputStream(CharStreams.fromString(unit.text()));
    parser.setInputStream(new CommonTokenStream(lexer));
    LOG.fine(() -> "parse started: " + parser.getClass().getSimpleName());
    final ParserRuleContext root = startRule.apply(parser);
    final ParseResult result =
        new ParseResult(SyntaxTreeMapper.map(root, parser), errors.findings());
    LOG.fine(
        () ->
            "parse finished: " + parser.getClass().getSimpleName() + " diagnostics="
                + result.diagnostics().size());
    return result;
  }
}
