package dev.ward.analysis.domain.rule.metric;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** Limits reached exactly, end-of-file lines, underscores, compound assignments and nesting. */
final class MeasurementBoundaryTest {

  private static final String FUNCTION = "funcdef";
  private static final int NESTED_PARAMETERS = 5;
  private static final int EMPTY_END = 3;
  private static final String NAME = "name";
  private static final String TOKEN_NAME = "NAME";
  private static final String TYPED_ARGS = "typedargslist";
  private static final String TFPDEF = "tfpdef";
  private static final String LINES = "measure=lines; node=funcdef; max=2; message=lines {value}";
  private static final String DECISIONS =
      "measure=decision-points; node=funcdef; max=2; nested=funcdef; decisions=if_stmt; message=complexity {value}";
  private static final String IDENTIFIERS =
      "measure=identifier-case; node=funcdef:snake,expr_stmt:snake-or-upper; name=name; message={name} {expected}";
  private static final String PARAMETERS =
      "measure=parameters; node=funcdef; max=4; name=name; path=parameters/typedargslist/tfpdef; receivers=self; message={value}";
  private static final String TWO_PARAMETERS =
      "measure=parameters; node=funcdef; max=2; name=name; path=parameters/typedargslist/tfpdef; receivers=self; message={value}";

  @Test
  void linesAtTheLimitIsNotAViolation() {
    assertThat(Trees.messages(LINES, Trees.node(FUNCTION, 1, 2))).isEmpty();
  }

  @Test
  void linesOfANodeWithoutChildrenAreItsSpan() {
    assertThat(Trees.messages(LINES, Trees.node(FUNCTION, 1, EMPTY_END)))
        .containsExactly("lines " + EMPTY_END);
  }

  @Test
  void aFileWithoutAnEndOfInputTokenIsNotShortened() {
    final SyntaxNode file = Trees.node("file_input", 1, 3, Trees.node("stmt", 1, 3));

    assertThat(Trees.messages("measure=lines; node=file_input; max=2; message=file {value}", file))
        .containsExactly("file 3");
  }

  @Test
  void decisionPointsAtTheLimitIsNotAViolation() {
    final SyntaxNode function =
        Trees.node(FUNCTION, 1, 2, Trees.named(NAME, "f", 1), Trees.node("if_stmt", 2, 2));

    assertThat(Trees.messages(DECISIONS, function)).isEmpty();
  }

  @Test
  void underscoresAreStrippedBeforeTheCheck() {
    final SyntaxNode module =
        Trees.node(
            "file_input",
            1,
            3,
            Trees.node(FUNCTION, 1, 1, Trees.named(NAME, "__", 1)),
            Trees.node(FUNCTION, 2, 2, Trees.named(NAME, "_load_config", 2)),
            Trees.node(FUNCTION, 3, 3, Trees.named(NAME, "_loadConfig", 3)));

    assertThat(Trees.messages(IDENTIFIERS, module)).containsExactly("_loadConfig snake_case");
  }

  @Test
  void compoundAssignmentIsNotAPlainAssignment() {
    final SyntaxNode statement =
        Trees.node(
            "expr_stmt",
            1,
            1,
            Trees.node("testlist_star_expr", 1, 1, Trees.token(TOKEN_NAME, "camelCase", 1, 1)),
            Trees.token("'+='", "+=", 1, 11));

    assertThat(Trees.messages(IDENTIFIERS, statement)).isEmpty();
  }

  @Test
  void nestedFunctionInsideAMethodIsNotAMethod() {
    final SyntaxNode inner = Trees.function("inner", 3, "self", "a", "b", "c", "d");
    final SyntaxNode method =
        Trees.withBody(
            Trees.function("m", 2, "self", "a", "b", "c"), Trees.node("block", 3, 3, inner));
    final SyntaxNode classBody =
        Trees.node("classdef", 1, 3, Trees.named(NAME, "A", 1), Trees.node("block", 2, 3, method));

    assertThat(Trees.messages(PARAMETERS, classBody))
        .containsExactly(String.valueOf(NESTED_PARAMETERS));
  }

  @Test
  void parameterListIgnoresChildrenThatAreNotParameters() {
    final SyntaxNode list =
        Trees.node(
            TYPED_ARGS,
            1,
            1,
            Trees.node(TFPDEF, 1, 1, Trees.named(NAME, "a", 1)),
            Trees.node("test", 1, 1, Trees.token(TOKEN_NAME, "1", 1, 5)),
            Trees.node(TFPDEF, 1, 1, Trees.named(NAME, "b", 1)));

    assertThat(Trees.messages(TWO_PARAMETERS, Trees.withParameters(list, "f"))).isEmpty();
  }
}
