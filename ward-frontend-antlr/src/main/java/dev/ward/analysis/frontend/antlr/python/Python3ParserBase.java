package dev.ward.analysis.frontend.antlr.python;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

/** Parser helper methods used by the Python grammar's semantic predicates. */
public abstract class Python3ParserBase extends Parser {
  /**
   * Creates the parser over the given token stream.
   *
   * @param input the tokens to parse
   */
  protected Python3ParserBase(final TokenStream input) {
    super(input);
  }

  /**
   * Tells whether a plus or minus cannot start a new expression here.
   *
   * @return always {@code true}; the grammar relies on it only as a predicate
   */
  public boolean cannotBePlusMinus() {
    return true;
  }

  /**
   * Tells whether a dot, left parenthesis or equals sign cannot start a new expression here.
   *
   * @return always {@code true}; the grammar relies on it only as a predicate
   */
  public boolean cannotBeDotLpEq() {
    return true;
  }
}
