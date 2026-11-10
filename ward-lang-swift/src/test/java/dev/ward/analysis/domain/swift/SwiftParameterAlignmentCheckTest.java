package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SwiftParameterAlignmentCheckTest {

  private static final int FIRST_COLUMN = 16;
  private static final int OTHER_COLUMN = 9;

  private static SyntaxNode parameter(final int line, final int column) {
    final Span span =
        new Span(line, column, line, column + 1, line * 100 + column, line * 100 + column + 1);
    return new SyntaxNode("parameter", span, Map.of(), List.of());
  }

  private static SyntaxNode function(final String bodyKind, final SyntaxNode... parameters) {
    final Span span = new Span(1, 1, 3, 1, 0, 400);
    final SyntaxNode body = new SyntaxNode(bodyKind, span, Map.of(), List.of(parameter(3, 1)));
    final SyntaxNode signature =
        new SyntaxNode("function_signature", span, Map.of(), List.of(parameters));
    return new SyntaxNode("function_declaration", span, Map.of(), List.of(signature, body));
  }

  private static List<RuleMatch> run(final SyntaxNode function) {
    return new SwiftParameterAlignmentCheck("misaligned")
        .matches(new SyntaxNode("file", function.span(), Map.of(), List.of(function)));
  }

  @Test
  void reportsAParameterOnANewLineAtAnotherColumn() {
    final List<RuleMatch> found =
        run(function("code_block", parameter(1, FIRST_COLUMN), parameter(2, OTHER_COLUMN)));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startLine()).isEqualTo(2);
  }

  @Test
  void acceptsAlignedParametersAndParametersOnTheFirstLine() {
    assertThat(run(function("code_block", parameter(1, FIRST_COLUMN), parameter(2, FIRST_COLUMN))))
        .isEmpty();
    assertThat(run(function("code_block", parameter(1, FIRST_COLUMN), parameter(1, OTHER_COLUMN))))
        .isEmpty();
  }

  @Test
  void ignoresParametersInsideTheBody() {
    assertThat(run(function("function_body", parameter(1, FIRST_COLUMN)))).isEmpty();
  }
}
