package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The percent and str.format parsers and the argument reader, on the shapes the rules judge. */
final class FormatStringParsersTest {

  private static final int THREE = 3;

  @Test
  void percentFormatCountsPositionalAndStarConversions() {
    final PercentFormatString parsed = PercentFormatString.parse("%s %*d");

    assertThat(parsed.positional()).isEqualTo(THREE);
    assertThat(parsed.star()).isTrue();
    assertThat(parsed.numeric()).isTrue();
    assertThat(parsed.named()).isEmpty();
  }

  @Test
  void percentFormatReadsNamedConversions() {
    final PercentFormatString parsed = PercentFormatString.parse("%(a)s %%");

    assertThat(parsed.named()).containsExactly("a");
    assertThat(parsed.positional()).isZero();
    assertThat(parsed.incomplete()).isFalse();
  }

  @Test
  void percentFormatReportsIncompleteAndUnsupported() {
    assertThat(PercentFormatString.parse("%").incomplete()).isTrue();
    assertThat(PercentFormatString.parse("%(a").incomplete()).isTrue();
    assertThat(PercentFormatString.parse("%y").unsupported()).contains('y');
  }

  @Test
  void dotFormatCountsFieldsAndNames() {
    final DotFormatString parsed = DotFormatString.parse("{}{x}{0}{{}}");

    assertThat(parsed.automatic()).isEqualTo(1);
    assertThat(parsed.names()).containsExactly("x");
    assertThat(parsed.manual()).containsExactly(0);
    assertThat(parsed.invalid()).isFalse();
  }

  @Test
  void dotFormatReadsNestedSpecsAndReportsUnbalancedBraces() {
    final DotFormatString nested = DotFormatString.parse("{:{}}");

    assertThat(nested.automatic()).isEqualTo(2);
    assertThat(nested.specs()).containsExactly("{}");
    assertThat(DotFormatString.parse("{").invalid()).isTrue();
    assertThat(DotFormatString.parse("}").invalid()).isTrue();
  }

  @Test
  void argumentReadsTuplesDictsStringsAndOthers() {
    assertThat(PercentArgument.of("(1,)").count()).isOne();
    assertThat(PercentArgument.of("(1,2)").count()).isEqualTo(2);
    assertThat(PercentArgument.of("()").kind()).isEqualTo(PercentArgument.Kind.TUPLE);
    assertThat(PercentArgument.of("(1)").kind()).isEqualTo(PercentArgument.Kind.OTHER);
    assertThat(PercentArgument.of("{\"a\":1}").keys()).contains(List.of("a"));
    assertThat(PercentArgument.of("{k:1}").keys()).isEmpty();
    assertThat(PercentArgument.of("'x'").kind()).isEqualTo(PercentArgument.Kind.STRING);
    assertThat(PercentArgument.of("name").kind()).isEqualTo(PercentArgument.Kind.OTHER);
  }

  @Test
  void splitRespectsBracketsAndLiterals() {
    assertThat(PercentArgument.split("f(a,b),\"c,d\",e", ','))
        .containsExactly("f(a,b)", "\"c,d\"", "e");
    assertThat(PercentArgument.split("", ',')).containsExactly("");
  }
}
