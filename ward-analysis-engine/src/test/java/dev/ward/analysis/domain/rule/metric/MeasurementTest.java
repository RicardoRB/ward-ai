package dev.ward.analysis.domain.rule.metric;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The four measurements, run through the matcher over hand-built trees. */
final class MeasurementTest {

  private static final String FUNCTION = "funcdef";
  private static final String IF_STMT = "if_stmt";
  private static final String IF_TOKEN = "'if'";
  private static final String DEF_TOKEN = "'def'";
  private static final String NAME = "name";
  private static final String IDENTIFIER = "NAME";
  private static final int SIX_PARAMETERS = 6;
  private static final String FUNCTION_SPANS_THREE = "function f spans 3 lines";

  private static final String FUNCTION_LENGTH =
      "measure=lines; node=funcdef; max=2; name=name; message=function {name} spans {value} lines";

  private static final String PARAMETERS =
      "measure=parameters; node=funcdef; max=5; name=name; path=parameters/typedargslist/tfpdef; receivers=self,cls; message=function {name} has {value} parameters";

  private static final String DECISIONS =
      "measure=decision-points; node=funcdef; max=6; name=name; nested=funcdef,classdef,lambdef; decisions=if_stmt,while_stmt,test>if,and_test>and,or_test>or,if_stmt>elif; message=function {name} has complexity {value}";

  private static final String IDENTIFIERS =
      "measure=identifier-case; node=funcdef:snake,classdef:cap,expr_stmt:snake-or-upper; name=name; message={name} should be {expected}";

  @Test
  void linesReportsANodeSpanningMoreThanTheLimit() {
    final SyntaxNode function =
        Trees.node(FUNCTION, 1, 3, Trees.token(DEF_TOKEN, "def", 1, 1), Trees.named(NAME, "f", 1));

    assertThat(Trees.messages(FUNCTION_LENGTH, function)).containsExactly(FUNCTION_SPANS_THREE);
  }

  @Test
  void linesCountsTheSpanTheMapperGives() {
    // The mapper ends a file's span on its last real line, so the end-of-input token sits after it.
    final SyntaxNode file =
        new SyntaxNode(
            "file_input",
            Trees.span(1, 1, 3, 1),
            java.util.Map.of(),
            List.of(
                Trees.node("stmt", 1, 3),
                new SyntaxNode(
                    "EOF", Trees.span(4, 1, 4, 1), java.util.Map.of("text", "<EOF>"), List.of())));

    assertThat(Trees.messages("measure=lines; node=file_input; max=2; message=file {value}", file))
        .containsExactly("file 3");
  }

  @Test
  void parametersLeaveOutTheReceiverOfAMethod() {
    final SyntaxNode method = method(SIX_PARAMETERS);
    final SyntaxNode classBody =
        Trees.node("classdef", 1, 1, Trees.named(NAME, "A", 1), Trees.node("block", 1, 1, method));

    assertThat(Trees.messages(PARAMETERS, classBody)).isEmpty();
  }

  @Test
  void parametersCountEveryParameterOfAFunction() {
    assertThat(Trees.messages(PARAMETERS, method(SIX_PARAMETERS)))
        .containsExactly("function f has 6 parameters");
  }

  @Test
  void decisionPointsCountTokensAndNodesButNotNestedScopes() {
    final SyntaxNode function =
        Trees.node(
            FUNCTION,
            1,
            9,
            Trees.named(NAME, "f", 1),
            Trees.node(
                IF_STMT,
                2,
                3,
                Trees.token(IF_TOKEN, "if", 2, 5),
                Trees.token("'elif'", "elif", 3, 1)),
            Trees.node("while_stmt", 4, 4),
            Trees.node("test", 5, 5, Trees.token(IF_TOKEN, "if", 5, 9)),
            Trees.node("and_test", 6, 6, Trees.token("'and'", "and", 6, 5)),
            Trees.node("or_test", 7, 7, Trees.token("'or'", "or", 7, 5)),
            Trees.node(
                FUNCTION,
                8,
                9,
                Trees.node(IF_STMT, 8, 8),
                Trees.node(IF_STMT, 8, 8),
                Trees.node(IF_STMT, 8, 8)));

    assertThat(Trees.messages(DECISIONS, function)).containsExactly("function f has complexity 7");
  }

  @Test
  void identifierCaseChecksDeclarationsAndPlainAssignmentsOnly() {
    final SyntaxNode module =
        Trees.node(
            "file_input",
            1,
            12,
            Trees.node(FUNCTION, 1, 1, Trees.named(NAME, "loadConfig", 1)),
            Trees.node(FUNCTION, 2, 2, Trees.named(NAME, "__init__", 2)),
            Trees.node(FUNCTION, 3, 3, Trees.named(NAME, "_", 3)),
            Trees.node("classdef", 4, 4, Trees.named(NAME, "user_store", 4)),
            assignment("myVar", 5),
            assignment("MAX_SIZE", 6),
            assignment("a.b", 7),
            Trees.node(
                "expr_stmt",
                8,
                8,
                Trees.node("testlist_star_expr", 8, 8, Trees.token(IDENTIFIER, "x", 8, 1)),
                Trees.token("'+='", "+=", 8, 3)));

    assertThat(Trees.messages(IDENTIFIERS, module))
        .containsExactly(
            "loadConfig should be snake_case",
            "user_store should be CapWords",
            "myVar should be snake_case or UPPER_SNAKE_CASE");
  }

  /** A function {@code f} with {@code count} parameters, the first named {@code self}. */
  private static SyntaxNode method(final int count) {
    final List<SyntaxNode> items = new java.util.ArrayList<>();
    for (int index = 0; index < count; index++) {
      final String name = index == 0 ? "self" : "p" + index;
      items.add(Trees.node("tfpdef", 1, 1, Trees.named(NAME, name, 1)));
    }
    final SyntaxNode typedArgs =
        Trees.node("typedargslist", 1, 1, items.toArray(SyntaxNode[]::new));
    return Trees.node(
        FUNCTION, 1, 1, Trees.named(NAME, "f", 1), Trees.node("parameters", 1, 1, typedArgs));
  }

  private static SyntaxNode assignment(final String target, final int line) {
    return Trees.node(
        "expr_stmt",
        line,
        line,
        Trees.node("testlist_star_expr", line, line, Trees.token(IDENTIFIER, target, line, 1)),
        Trees.token("'='", "=", line, target.length() + 2));
  }
}
