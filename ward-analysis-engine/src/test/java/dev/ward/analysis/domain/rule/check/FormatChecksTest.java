package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The percent, str.format and logging checks, on hand-built trees of each operator and call. */
final class FormatChecksTest {

  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);
  private static final String PERCENT = "%";
  private static final String TEXT = "text";

  @Test
  void percentChecksReportEachProblemOnItsOperation() {
    assertThat(percent(PercentFormatCheck.Problem.INVALID, "\"%\"", "1")).hasSize(1);
    assertThat(percent(PercentFormatCheck.Problem.UNSUPPORTED, "\"%y\"", "1")).hasSize(1);
    assertThat(percent(PercentFormatCheck.Problem.MIXED, "\"%s %(x)s\"", "{\"x\":1}")).hasSize(1);
    assertThat(percent(PercentFormatCheck.Problem.EXPECTED_MAPPING, "\"%(x)s\"", "(1,)"))
        .hasSize(1);
    assertThat(percent(PercentFormatCheck.Problem.STAR_MAPPING, "\"%*s\"", "{\"x\":1}")).hasSize(1);
    assertThat(percent(PercentFormatCheck.Problem.COUNT, "\"%s %s\"", "(1,)")).hasSize(1);
    assertThat(percent(PercentFormatCheck.Problem.UNUSED_KEYS, "\"%(x)s\"", "{\"x\":1,\"y\":2}"))
        .hasSize(1);
    assertThat(percent(PercentFormatCheck.Problem.MISSING_KEYS, "\"%(x)s\"", "{\"y\":1}"))
        .hasSize(1);
    assertThat(percent(PercentFormatCheck.Problem.TYPE_MISMATCH, "\"%d\"", "\"x\"")).hasSize(1);
  }

  @Test
  void percentChecksLeaveValidOperationsAlone() {
    assertThat(percent(PercentFormatCheck.Problem.COUNT, "\"%s\"", "1")).isEmpty();
    assertThat(percent(PercentFormatCheck.Problem.UNUSED_KEYS, "\"%(x)s\"", "{\"x\":1}")).isEmpty();
    assertThat(percent(PercentFormatCheck.Problem.MISSING_KEYS, "\"%(x)s\"", "{\"x\":1}"))
        .isEmpty();
    assertThat(percent(PercentFormatCheck.Problem.TYPE_MISMATCH, "'%s'", "\"x\"")).isEmpty();
  }

  @Test
  void percentChecksIgnoreOtherOperatorsAndNonLiteralFormats() {
    final SyntaxNode notModulo =
        new SyntaxNode("expr", SPAN, Map.of(), List.of(leaf("a"), leaf("+"), leaf("b")));
    assertThat(
            new PercentFormatCheck(PercentFormatCheck.Problem.INVALID, "message")
                .matches(notModulo))
        .isEmpty();
    assertThat(percent(PercentFormatCheck.Problem.INVALID, "name", "1")).isEmpty();
    assertThat(percent(PercentFormatCheck.Problem.INVALID, "\"a\"\"b\"", "1")).isEmpty();
  }

  @Test
  void dotChecksReportEachProblemAndSkipStarArguments() {
    assertThat(dot(DotFormatCheck.Problem.UNUSED_POSITIONAL, "\"{}\".format(*a)")).isEmpty();
    assertThat(dot(DotFormatCheck.Problem.INVALID, "\"{}\".upper()")).isEmpty();
    assertThat(dot(DotFormatCheck.Problem.INVALID, "name.format(1)")).isEmpty();
    assertThat(dot(DotFormatCheck.Problem.INVALID, "\"{\".format(1)")).hasSize(1);
    assertThat(dot(DotFormatCheck.Problem.UNUSED_NAMED, "\"{x}\".format(x=1,y=2)")).hasSize(1);
    assertThat(dot(DotFormatCheck.Problem.UNUSED_POSITIONAL, "\"{}\".format(1,2)")).hasSize(1);
    assertThat(dot(DotFormatCheck.Problem.MISSING, "\"{}{}\".format(1)")).hasSize(1);
    assertThat(dot(DotFormatCheck.Problem.MIXED, "\"{}{0}\".format(1,2)")).hasSize(1);
    assertThat(dot(DotFormatCheck.Problem.BAD_CHARACTER, "\"{:y}\".format(1)")).hasSize(1);
  }

  @Test
  void loggingChecksCountArgumentsAgainstPlaceholders() {
    assertThat(logging(LoggingArgsCheck.Problem.TOO_MANY, "logging.info(\"%s\",1,2)")).hasSize(1);
    assertThat(logging(LoggingArgsCheck.Problem.TOO_FEW, "logging.info(\"%s %s\",1)")).hasSize(1);
    assertThat(logging(LoggingArgsCheck.Problem.TOO_MANY, "logging.info(\"%s\",1,exc_info=True)"))
        .isEmpty();
    assertThat(logging(LoggingArgsCheck.Problem.TOO_MANY, "logging.info(\"%s\",*a)")).isEmpty();
    assertThat(logging(LoggingArgsCheck.Problem.TOO_MANY, "other.info(\"%s\",1,2)")).isEmpty();
  }

  private static List<RuleMatch> percent(
      final PercentFormatCheck.Problem problem, final String left, final String right) {
    final SyntaxNode operation =
        new SyntaxNode(
            "expr",
            SPAN,
            Map.of(),
            List.of(
                new SyntaxNode("expr", SPAN, Map.of(), List.of(leaf(left))),
                leaf(PERCENT),
                new SyntaxNode("expr", SPAN, Map.of(), List.of(leaf(right)))));
    return new PercentFormatCheck(problem, "message").matches(operation);
  }

  private static List<RuleMatch> dot(final DotFormatCheck.Problem problem, final String text) {
    return new DotFormatCheck(problem, "message").matches(call(text));
  }

  private static List<RuleMatch> logging(
      final LoggingArgsCheck.Problem problem, final String text) {
    return new LoggingArgsCheck(problem, "message").matches(call(text));
  }

  private static SyntaxNode call(final String text) {
    return new SyntaxNode("atom_expr", SPAN, Map.of(), List.of(leaf(text)));
  }

  private static SyntaxNode leaf(final String text) {
    return new SyntaxNode("TOKEN", SPAN, Map.of(TEXT, text), List.of());
  }
}
