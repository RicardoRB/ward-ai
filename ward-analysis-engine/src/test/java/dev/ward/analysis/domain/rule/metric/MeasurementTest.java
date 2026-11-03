package dev.ward.analysis.domain.rule.metric;

import static dev.ward.analysis.domain.rule.metric.Trees.named;
import static dev.ward.analysis.domain.rule.metric.Trees.node;
import static dev.ward.analysis.domain.rule.metric.Trees.token;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
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
        node(FUNCTION, 1, 3, token(DEF_TOKEN, "def", 1, 1), named(NAME, "f", 1));

    assertThat(messages(FUNCTION_LENGTH, function)).containsExactly(FUNCTION_SPANS_THREE);
  }

  @Test
  void linesDoesNotCountTheLineAfterTheEndOfInput() {
    final SyntaxNode file =
        new SyntaxNode(
            "file_input",
            Trees.span(1, 1, 4, 1),
            java.util.Map.of(),
            List.of(
                node("stmt", 1, 3),
                new SyntaxNode(
                    "EOF", Trees.span(4, 1, 4, 1), java.util.Map.of("text", "<EOF>"), List.of())));

    assertThat(messages("measure=lines; node=file_input; max=2; message=file {value}", file))
        .containsExactly("file 3");
  }

  @Test
  void parametersLeaveOutTheReceiverOfAMethod() {
    final SyntaxNode method = method(SIX_PARAMETERS);
    final SyntaxNode classBody =
        node("classdef", 1, 1, named(NAME, "A", 1), node("block", 1, 1, method));

    assertThat(messages(PARAMETERS, classBody)).isEmpty();
  }

  @Test
  void parametersCountEveryParameterOfAFunction() {
    assertThat(messages(PARAMETERS, method(SIX_PARAMETERS)))
        .containsExactly("function f has 6 parameters");
  }

  @Test
  void decisionPointsCountTokensAndNodesButNotNestedScopes() {
    final SyntaxNode function =
        node(
            FUNCTION,
            1,
            9,
            named(NAME, "f", 1),
            node(IF_STMT, 2, 3, token(IF_TOKEN, "if", 2, 5), token("'elif'", "elif", 3, 1)),
            node("while_stmt", 4, 4),
            node("test", 5, 5, token(IF_TOKEN, "if", 5, 9)),
            node("and_test", 6, 6, token("'and'", "and", 6, 5)),
            node("or_test", 7, 7, token("'or'", "or", 7, 5)),
            node(FUNCTION, 8, 9, node(IF_STMT, 8, 8), node(IF_STMT, 8, 8), node(IF_STMT, 8, 8)));

    assertThat(messages(DECISIONS, function)).containsExactly("function f has complexity 7");
  }

  @Test
  void identifierCaseChecksDeclarationsAndPlainAssignmentsOnly() {
    final SyntaxNode module =
        node(
            "file_input",
            1,
            12,
            node(FUNCTION, 1, 1, named(NAME, "loadConfig", 1)),
            node(FUNCTION, 2, 2, named(NAME, "__init__", 2)),
            node(FUNCTION, 3, 3, named(NAME, "_", 3)),
            node("classdef", 4, 4, named(NAME, "user_store", 4)),
            assignment("myVar", 5),
            assignment("MAX_SIZE", 6),
            assignment("a.b", 7),
            node(
                "expr_stmt",
                8,
                8,
                node("testlist_star_expr", 8, 8, token(IDENTIFIER, "x", 8, 1)),
                token("'+='", "+=", 8, 3)));

    assertThat(messages(IDENTIFIERS, module))
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
      items.add(node("tfpdef", 1, 1, named(NAME, name, 1)));
    }
    final SyntaxNode typedArgs = node("typedargslist", 1, 1, items.toArray(SyntaxNode[]::new));
    return node(FUNCTION, 1, 1, named(NAME, "f", 1), node("parameters", 1, 1, typedArgs));
  }

  private static SyntaxNode assignment(final String target, final int line) {
    return node(
        "expr_stmt",
        line,
        line,
        node("testlist_star_expr", line, line, token(IDENTIFIER, target, line, 1)),
        token("'='", "=", line, target.length() + 2));
  }

  private static List<String> messages(final String body, final SyntaxNode root) {
    final RuleDefinition definition =
        new RuleDefinition(
            "ward:python/test",
            AnalysisLanguage.PYTHON,
            RuleCapability.SYNTAX,
            AnalysisSeverity.ERROR,
            body);
    return new MetricMatcher()
        .compile(definition).matches(root).stream().map(RuleMatch::message).toList();
  }
}
