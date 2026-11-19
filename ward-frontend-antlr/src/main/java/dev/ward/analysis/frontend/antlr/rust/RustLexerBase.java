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

Modified by Ward: derived from the lexer base of grammars-v4 (rust/Java/RustLexerBase.java), extends the
shared lexer base and is written in Ward's code style.
*/
package dev.ward.analysis.frontend.antlr.rust;

import dev.ward.analysis.frontend.antlr.WardLexerBase;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;

/**
 * Base class of the generated Rust lexer. Three predicates keep a float literal from swallowing a
 * tuple index ({@code t.0.1}) or a method call on an integer ({@code 1.max(2)}), and a shebang
 * line only counts at the start of the file.
 */
public abstract class RustLexerBase extends WardLexerBase {
  private static final int FLOAT_SUFFIX_LENGTH = 3;
  private static final int NO_TOKEN = -1;
  private static final char FLOAT_SUFFIX_START = 'f';
  private static final char DOT = '.';
  private static final char UNDERSCORE = '_';

  /** The types of the last two tokens on the default channel. */
  private int last = NO_TOKEN;

  private int beforeLast = NO_TOKEN;

  /**
   * Creates the lexer over the given input.
   *
   * @param input the character stream to lex
   */
  protected RustLexerBase(final CharStream input) {
    super(input);
  }

  @Override
  public Token nextToken() {
    final Token next = super.nextToken();
    if (next.getChannel() == Token.DEFAULT_CHANNEL) {
      beforeLast = last;
      last = next.getType();
    }
    return next;
  }

  @Override
  public void reset() {
    super.reset();
    last = NO_TOKEN;
    beforeLast = NO_TOKEN;
  }

  /** Returns whether the lexer is at the start of the file. */
  public boolean sof() {
    return _input.LA(-1) <= 0;
  }

  /** Returns whether a dot after a decimal literal belongs to the literal ({@code 1.}). */
  public boolean floatDotPossible() {
    final int next = _input.LA(1);
    if (next == DOT || next == UNDERSCORE) {
      return false;
    }
    if (next == FLOAT_SUFFIX_START) {
      return floatSuffixFollows();
    }
    return !isAsciiLetter(next);
  }

  private boolean floatSuffixFollows() {
    final String suffix = text(FLOAT_SUFFIX_LENGTH);
    return "f32".equals(suffix) || "f64".equals(suffix);
  }

  private String text(final int length) {
    final StringBuilder out = new StringBuilder();
    for (int i = 1; i <= length; i++) {
      out.appendCodePoint(Math.max(_input.LA(i), 0));
    }
    return out.toString();
  }

  private static boolean isAsciiLetter(final int c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
  }

  /** Returns whether a float literal may start here, which it may not right after a dot member. */
  public boolean floatLiteralPossible() {
    if (last == NO_TOKEN || beforeLast == NO_TOKEN || last != RustLexer.DOT) {
      return true;
    }
    return !blocksFloat(beforeLast);
  }

  private static boolean blocksFloat(final int type) {
    return switch (type) {
      case RustLexer.CHAR_LITERAL, RustLexer.STRING_LITERAL, RustLexer.RAW_STRING_LITERAL, RustLexer.BYTE_LITERAL, RustLexer.BYTE_STRING_LITERAL, RustLexer.RAW_BYTE_STRING_LITERAL, RustLexer.INTEGER_LITERAL, RustLexer.DEC_LITERAL, RustLexer.HEX_LITERAL, RustLexer.OCT_LITERAL, RustLexer.BIN_LITERAL, RustLexer.KW_SUPER, RustLexer.KW_SELFVALUE, RustLexer.KW_SELFTYPE, RustLexer.KW_CRATE, RustLexer.KW_DOLLARCRATE, RustLexer.GT, RustLexer.RCURLYBRACE, RustLexer.RSQUAREBRACKET, RustLexer.RPAREN, RustLexer.KW_AWAIT, RustLexer.NON_KEYWORD_IDENTIFIER, RustLexer.RAW_IDENTIFIER, RustLexer.KW_MACRORULES -> true;
      default -> false;
    };
  }
}
