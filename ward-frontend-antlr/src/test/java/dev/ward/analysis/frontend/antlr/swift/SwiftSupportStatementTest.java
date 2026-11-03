package dev.ward.analysis.frontend.antlr.swift;

import static org.assertj.core.api.Assertions.assertThat;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

class SwiftSupportStatementTest {

  private static CommonTokenStream stream(final String text) {
    final CommonTokenStream tokens =
        new CommonTokenStream(new SwiftLexer(CharStreams.fromString(text)));
    tokens.fill();
    return tokens;
  }

  /** Positions the stream on the first default-channel token with the given text. */
  private static CommonTokenStream at(final String text, final String tokenText) {
    final CommonTokenStream tokens = stream(text);
    for (final Token token : tokens.getTokens()) {
      if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getText().equals(tokenText)) {
        tokens.seek(token.getTokenIndex());
        return tokens;
      }
    }
    throw new IllegalArgumentException("no token " + tokenText);
  }

  @Test
  void newlineSeparatesStatements() {
    final CommonTokenStream tokens = at("a\nb", "b");

    assertThat(SwiftSupport.isSeparatedStatement(tokens, tokens.index())).isTrue();
  }

  @Test
  void sameLineStatementsAreNotSeparated() {
    final CommonTokenStream tokens = at("a b", "b");

    assertThat(SwiftSupport.isSeparatedStatement(tokens, tokens.index())).isFalse();
  }

  @Test
  void fillUpReadsToTheEndOfTheStream() {
    final CommonTokenStream tokens = stream("a + b");

    SwiftSupport.fillUp(tokens);

    assertThat(tokens.LA(1)).isNotEqualTo(-1);
  }

  @Test
  void operatorHeadAndCharacterClassifySingleCharacters() {
    final CommonTokenStream tokens = at("a + b", "+");
    final Token plus = tokens.get(tokens.index());

    assertThat(SwiftSupport.isOperatorHead(plus)).isTrue();
    assertThat(SwiftSupport.isOperatorCharacter(plus)).isTrue();
    assertThat(SwiftSupport.isOperatorHead(at("ab", "ab").get(0))).isFalse();
  }

  @Test
  void endOfInputCountsAsRightWhitespace() {
    final CommonTokenStream tokens = stream("a");
    final Token eof = tokens.get(tokens.size() - 1);

    assertThat(SwiftSupport.isRightOperatorWS(eof)).isTrue();
  }
}
