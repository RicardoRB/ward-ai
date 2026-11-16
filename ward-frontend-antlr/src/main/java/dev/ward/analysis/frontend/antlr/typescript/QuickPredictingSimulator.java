package dev.ward.analysis.frontend.antlr.typescript;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ParserATNSimulator;

/** Asks {@link QuickPrediction} first, and the simulator of the generated parser otherwise. */
final class QuickPredictingSimulator extends ParserATNSimulator {
  private final ParserATNSimulator adaptive;
  private final QuickPrediction quick;

  /**
   * Wraps the simulator of a parser.
   *
   * @param parser the parser
   * @param adaptive the simulator the generated parser created
   */
  QuickPredictingSimulator(final TypeScriptParserBase parser, final ParserATNSimulator adaptive) {
    super(parser, adaptive.atn, adaptive.decisionToDFA, adaptive.getSharedContextCache());
    this.adaptive = adaptive;
    this.quick = new QuickPrediction(parser);
  }

  /** Returns the simulator this one wraps. */
  ParserATNSimulator adaptive() {
    return adaptive;
  }

  @Override
  public int adaptivePredict(
      final TokenStream input,
      final int decision,
      final ParserRuleContext outerContext
  ) {
    final int alternative = quick.predict(decision, input);
    return alternative == QuickPrediction.UNDECIDED
        ? adaptive.adaptivePredict(input, decision, outerContext)
        : alternative;
  }
}
