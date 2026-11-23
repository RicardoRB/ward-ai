package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks which string builds only fill the query or the fragment of a constant host. */
class JavaQueryBuildsTest {
  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);
  private static final String BASE = "\"https://h.example/p?q=\"";

  private static SyntaxNode leaf(final String text) {
    return new SyntaxNode("t", SPAN, Map.of("text", text), List.of());
  }

  private static SyntaxNode plus(final SyntaxNode left, final String right) {
    return new SyntaxNode("expression", SPAN, Map.of(), List.of(left, leaf("+"), leaf(right)));
  }

  @Test
  void aConstantBaseWithAQueryMakesTheValuesClean() {
    assertThat(JavaQueryBuilds.concatenation(plus(plus(leaf(BASE), "v"), "w"))).isPresent();
    assertThat(JavaQueryBuilds.concatenation(plus(plus(leaf("\"http://h/\""), "\"?x=\""), "v")))
        .isPresent();
  }

  @Test
  void aValueBeforeTheQueryOrInTheHostIsNotClean() {
    assertThat(JavaQueryBuilds.concatenation(plus(leaf("\"https://h.example/\""), "v"))).isEmpty();
    assertThat(JavaQueryBuilds.concatenation(plus(leaf("v"), "\"?x=\""))).isEmpty();
    assertThat(JavaQueryBuilds.concatenation(plus(leaf("\"https://\""), "host"))).isEmpty();
    assertThat(JavaQueryBuilds.concatenation(plus(leaf("\"ftp://h/p?x=\""), "v"))).isEmpty();
    assertThat(JavaQueryBuilds.concatenation(plus(leaf(BASE), "\"w\""))).isEmpty();
  }

  @Test
  void aFormatWithAQueryBeforeItsFirstSpecifierMakesTheArgumentsClean() {
    final List<SyntaxNode> arguments = List.of(leaf("\"https://h/p?q=%s\""), leaf("v"));

    assertThat(JavaQueryBuilds.format("format", "String", arguments, SPAN)).isPresent();
    assertThat(JavaQueryBuilds.format("format", "java.lang.String", arguments, SPAN)).isPresent();
    assertThat(JavaQueryBuilds.format("format", "Other", arguments, SPAN)).isEmpty();
    assertThat(JavaQueryBuilds.format("format", "String", List.of(), SPAN)).isEmpty();
    assertThat(
        JavaQueryBuilds.format(
            "format",
            "String",
            List.of(leaf("\"https://%s/p?q=\""), leaf("v")),
            SPAN
        )
    ).isEmpty();
    assertThat(JavaQueryBuilds.format("format", "String", List.of(leaf("f"), leaf("v")), SPAN))
        .isEmpty();
  }

  @Test
  void formattedOnALiteralFollowsTheSameRule() {
    final List<SyntaxNode> values = List.of(leaf("v"));

    assertThat(
        JavaQueryBuilds.format("formatted", "\"https://h/p?q=%s\"", values, SPAN)
    ).isPresent();
    assertThat(JavaQueryBuilds.format("formatted", "\"https://%s/p?q=\"", values, SPAN)).isEmpty();
    assertThat(JavaQueryBuilds.format("formatted", "text", values, SPAN)).isEmpty();
    assertThat(JavaQueryBuilds.format("other", "String", values, SPAN)).isEmpty();
  }
}
