package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.LexerNoViableAltException;

/**
 * Base class of every generated lexer. It reports a character that matches no rule as a syntax
 * finding. ANTLR reports it through an error listener, whose method has six parameters that the
 * interface fixes; overriding {@link #notifyListeners} avoids that signature.
 */
public abstract class WardLexerBase extends Lexer {

  private final SyntaxFindings findings = new SyntaxFindings();

  /**
   * Creates the lexer over the given input.
   *
   * @param input the character stream to lex
   */
  protected WardLexerBase(final CharStream input) {
    super(input);
  }

  /**
   * Starts a new list of lexer errors for the named unit.
   *
   * @param unitPath the project-root-relative path of the unit
   */
  public void startFindings(final String unitPath) {
    findings.start(unitPath);
  }

  /**
   * Returns the lexer errors since the last {@link #startFindings}.
   *
   * @return a copy of the lexer errors
   */
  public List<AnalysisFinding> findings() {
    return findings.findings();
  }

  @Override
  public void notifyListeners(final LexerNoViableAltException error) {
    findings.add(
        _tokenStartLine,
        _tokenStartCharPositionInLine + 1,
        "token recognition error at: '" + getText() + "'");
  }
}
