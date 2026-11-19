/*
Copyright (c) 2010 The Rust Project Developers
Copyright (c) 2020-2022 Student Main

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice (including the next paragraph) shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Modified by Ward: derived from the parser base of grammars-v4 (rust/Java/RustParserBase.java).
*/
package dev.ward.analysis.frontend.antlr.rust;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

/**
 * Base class of the generated Rust parser. The lexer has no {@code <<} and {@code >>} tokens, so a
 * shift is two adjacent angle brackets that the parser joins by looking at the next token.
 */
public abstract class RustParserBase extends Parser {
  /**
   * Creates the parser over the given tokens.
   *
   * @param input the token stream to read
   */
  protected RustParserBase(final TokenStream input) {
    super(input);
  }

  /**
   * Returns whether the next token has the given text, for the words Rust keeps contextual.
   *
   * @param word the text the token must have
   */
  public boolean textIs(final String word) {
    return word.equals(_input.LT(1).getText());
  }

  /** Returns whether the next token is {@code >}. */
  public boolean nextGt() {
    return _input.LA(1) == RustParser.GT;
  }

  /** Returns whether the next token is {@code <}. */
  public boolean nextLt() {
    return _input.LA(1) == RustParser.LT;
  }
}
