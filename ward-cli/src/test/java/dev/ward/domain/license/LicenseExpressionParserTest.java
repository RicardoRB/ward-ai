package dev.ward.domain.license;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LicenseExpressionParserTest {

  static Stream<Arguments> valid() {
    return Stream.of(
        Arguments.of("MIT", "MIT"),
        Arguments.of("mit or apache-2.0", "mit OR apache-2.0"),
        Arguments.of(
            "MIT AND BSD-3-Clause OR GPL-3.0-only", "MIT AND BSD-3-Clause OR GPL-3.0-only"),
        Arguments.of(
            "(MIT OR GPL-3.0-only) AND BSD-3-Clause", "(MIT OR GPL-3.0-only) AND BSD-3-Clause"),
        Arguments.of(
            "GPL-2.0-only WITH Classpath-exception-2.0",
            "GPL-2.0-only WITH Classpath-exception-2.0"),
        Arguments.of("GPL-3.0+", "GPL-3.0+"),
        Arguments.of("LicenseRef-Custom", "LicenseRef-Custom"),
        Arguments.of("((MIT))", "MIT"));
  }

  @ParameterizedTest
  @MethodSource("valid")
  void parsesAndRendersExpressions(final String text, final String rendered) {
    assertThat(LicenseExpressionParser.parse(text))
        .hasValueSatisfying(e -> assertThat(e.render()).isEqualTo(rendered));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "See LICENSE file",
        "Apache License, Version 2.0",
        "MIT OR",
        "AND MIT",
        "(MIT",
        "MIT)",
        "MIT WITH",
        "MIT MIT",
        "OR"
      })
  void rejectsMalformedText(final String text) {
    assertThat(LicenseExpressionParser.parse(text)).isEmpty();
  }

  @org.junit.jupiter.api.Test
  void andBindsTighterThanOr() {
    final LicenseExpression expression =
        LicenseExpressionParser.parse("A OR B AND C").orElseThrow();

    assertThat(expression).isInstanceOf(LicenseExpression.Or.class);
    assertThat(((LicenseExpression.Or) expression).right())
        .isInstanceOf(LicenseExpression.And.class);
    assertThat(expression.identifiers()).containsExactly("A", "B", "C");
  }

  @org.junit.jupiter.api.Test
  void parenthesesOverridePrecedence() {
    final LicenseExpression expression =
        LicenseExpressionParser.parse("(A OR B) AND C").orElseThrow();

    assertThat(expression).isInstanceOf(LicenseExpression.And.class);
  }
}
