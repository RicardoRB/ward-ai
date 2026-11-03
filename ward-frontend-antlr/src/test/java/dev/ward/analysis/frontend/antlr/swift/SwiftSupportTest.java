package dev.ward.analysis.frontend.antlr.swift;

import static org.assertj.core.api.Assertions.assertThat;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

class SwiftSupportTest {

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
  void spacedOperatorIsBinary() {
    final CommonTokenStream tokens = at("a + b", "+");

    assertThat(SwiftSupport.isBinaryOp(tokens)).isTrue();
    assertThat(SwiftSupport.isPrefixOp(tokens)).isFalse();
    assertThat(SwiftSupport.isPostfixOp(tokens)).isFalse();
  }

  @Test
  void operatorWithoutSpaceAroundItIsBinary() {
    assertThat(SwiftSupport.isBinaryOp(at("a+b", "+"))).isTrue();
  }

  @Test
  void operatorWithSpaceOnlyOnTheLeftIsPrefix() {
    final CommonTokenStream tokens = at("a +b", "+");

    assertThat(SwiftSupport.isPrefixOp(tokens)).isTrue();
    assertThat(SwiftSupport.isBinaryOp(tokens)).isFalse();
  }

  @Test
  void operatorWithSpaceOnlyOnTheRightIsPostfix() {
    final CommonTokenStream tokens = at("a+ b", "+");

    assertThat(SwiftSupport.isPostfixOp(tokens)).isTrue();
    assertThat(SwiftSupport.isBinaryOp(tokens)).isFalse();
  }

  @Test
  void operatorFollowedByDotIsPostfix() {
    assertThat(SwiftSupport.isPostfixOp(at("a+.b", "+"))).isTrue();
  }

  @Test
  void isOperatorComparesTheOperatorText() {
    assertThat(SwiftSupport.isOperator(at("a + b", "+"), "+")).isTrue();
    assertThat(SwiftSupport.isOperator(at("a + b", "+"), "-")).isFalse();
  }

  @Test
  void identifierIsNotAnOperator() {
    final CommonTokenStream tokens = at("a + b", "a");

    assertThat(SwiftSupport.isOpNext(tokens)).isFalse();
    assertThat(SwiftSupport.getLastOpTokenIndex(tokens)).isEqualTo(-1);
    assertThat(SwiftSupport.isOperator(tokens, "+")).isFalse();
  }

  @Test
  void operatorStartsAtItsPosition() {
    assertThat(SwiftSupport.isOpNext(at("a + b", "+"))).isTrue();
  }
}
