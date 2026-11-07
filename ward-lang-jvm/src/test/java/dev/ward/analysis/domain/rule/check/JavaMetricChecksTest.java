package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Runs the Java size and metric checks over small hand-made syntax trees. */
class JavaMetricChecksTest {

  private static final String MESSAGE = "{value} over {max}";
  private static final String BLOCK = "block";
  private static final String CREATED_NAME = "createdName";
  private static final String PATTERN_NONE = "^$";
  private static final String HTTP = "http";
  private static final int LINE_LIMIT = 20;
  private static final int TAB_WIDTH = 8;
  private static final int LONG_LINE = 30;
  private static final int NARROW_TAB = 4;
  private static final int TAB_LIMIT_LOW = 4;
  private static final int TAB_LIMIT_HIGH = 5;
  private static final int BODY_LAST_LINE = 5;
  private static final int LOW_LIMIT = 1;
  private static final int MID_LIMIT = 2;
  private static final int NPATH_TWO_IFS = 3;
  private static final int NPATH_FOUR = 4;
  private static final int NCSS_WIDE = 100;
  private static final int NCSS_TWO = 2;
  private static final int NCSS_THREE = 3;
  private static final int NCSS_LAST_LINE = 4;
  private static final int CREATION_LAST_LINE = 12;
  private static final int BODY_LINES = 10;
  private static final int ANON_LIMIT = 5;
  private static final int MEMBER_LAST_LINE = 4;
  private static final int COUPLING_HIGH = 2;

  @Test
  void lineLengthReportsOnlyLinesOverTheLimitThatTheIgnorePatternDoesNotFind() {
    final String text = "short\n" + "x".repeat(LONG_LINE) + "\n// https://" + "y".repeat(LONG_LINE);
    final List<RuleMatch> matches =
        new LineLengthCheck(LINE_LIMIT, TAB_WIDTH, HTTP, MESSAGE)
            .check(MetricTrees.tree("compilationUnit", 1, 1), new SourceFile("F.java", text));

    assertThat(matches).hasSize(LOW_LIMIT);
    assertThat(matches.get(0).span().startLine()).isEqualTo(2);
  }

  @Test
  void lineLengthExpandsTabsToTheTabWidth() {
    final SourceFile file = new SourceFile("F.java", "\tx");
    final SyntaxNode root = MetricTrees.tree("compilationUnit", 1, 1);

    assertThat(
            new LineLengthCheck(TAB_LIMIT_LOW, NARROW_TAB, PATTERN_NONE, MESSAGE).check(root, file))
        .hasSize(LOW_LIMIT);
    assertThat(
            new LineLengthCheck(TAB_LIMIT_HIGH, NARROW_TAB, PATTERN_NONE, MESSAGE)
                .check(root, file))
        .isEmpty();
  }

  @Test
  void linesWithoutEmptyLinesCountOnlyLinesThatStartCode() {
    final SyntaxNode body =
        MetricTrees.tree(
            MetricTrees.METHOD_BODY,
            1,
            BODY_LAST_LINE,
            MetricTrees.token("'{'", 1),
            MetricTrees.token("'}'", BODY_LAST_LINE));

    assertThat(
            new LinesCheck(Set.of(MetricTrees.METHOD_BODY), MID_LIMIT, MESSAGE, false)
                .matches(body))
        .isEmpty();
    assertThat(
            new LinesCheck(Set.of(MetricTrees.METHOD_BODY), MID_LIMIT, MESSAGE, true).matches(body))
        .hasSize(LOW_LIMIT);
  }

  @Test
  void booleanOperatorsCountOnlyOutermostChains() {
    final SyntaxNode chain =
        MetricTrees.tree(
            MetricTrees.EXPRESSION,
            1,
            1,
            MetricTrees.tree(MetricTrees.EXPRESSION, 1, 1),
            MetricTrees.token("'&&'", 1),
            MetricTrees.tree(
                MetricTrees.EXPRESSION,
                1,
                1,
                MetricTrees.tree(MetricTrees.EXPRESSION, 1, 1),
                MetricTrees.token("'||'", 1),
                MetricTrees.tree(MetricTrees.EXPRESSION, 1, 1)));

    assertThat(new BooleanOperatorCheck(LOW_LIMIT, MESSAGE).matches(chain)).hasSize(LOW_LIMIT);
    assertThat(new BooleanOperatorCheck(MID_LIMIT, MESSAGE).matches(chain)).isEmpty();
  }

  @Test
  void npathMultipliesSequentialIfStatements() {
    final SyntaxNode method =
        MetricTrees.tree(
            "methodDeclaration",
            1,
            NPATH_TWO_IFS,
            MetricTrees.tree(
                MetricTrees.METHOD_BODY,
                1,
                NPATH_TWO_IFS,
                MetricTrees.tree(
                    BLOCK, 1, NPATH_TWO_IFS, MetricTrees.ifBlock(), MetricTrees.ifBlock())));

    assertThat(new NPathCheck(NPATH_TWO_IFS, MESSAGE).matches(method)).hasSize(LOW_LIMIT);
    assertThat(new NPathCheck(NPATH_FOUR, MESSAGE).matches(method)).isEmpty();
  }

  @Test
  void ncssCountsStatementsOfAMethodAndAddsOne() {
    final SyntaxNode method =
        MetricTrees.tree(
            "methodDeclaration",
            1,
            NCSS_LAST_LINE,
            MetricTrees.statement(),
            MetricTrees.statement());

    assertThat(new NcssCheck(NCSS_TWO, NCSS_WIDE, NCSS_WIDE, MESSAGE).matches(method))
        .hasSize(LOW_LIMIT);
    assertThat(new NcssCheck(NCSS_THREE, NCSS_WIDE, NCSS_WIDE, MESSAGE).matches(method)).isEmpty();
  }

  @Test
  void methodCountReportsTotalAndEachVisibilityOverItsLimit() {
    final SyntaxNode body =
        MetricTrees.tree(
            "classBody",
            1,
            MEMBER_LAST_LINE,
            MetricTrees.member(MetricTrees.token("private", 1)),
            MetricTrees.member(MetricTrees.token("public", 2)));

    final MethodCountCheck.Limits strict =
        new MethodCountCheck.Limits(LOW_LIMIT, 0, NCSS_THREE, NCSS_THREE, NCSS_THREE);
    assertThat(new MethodCountCheck(strict, MESSAGE).matches(body)).hasSize(COUPLING_HIGH);
    final MethodCountCheck.Limits loose =
        new MethodCountCheck.Limits(COUPLING_HIGH, LOW_LIMIT, NCSS_THREE, NCSS_THREE, NCSS_THREE);
    assertThat(new MethodCountCheck(loose, MESSAGE).matches(body)).isEmpty();
  }

  @Test
  void classCouplingCountsDistinctSimpleNamesThatAreNotExcluded() {
    final SyntaxNode klass =
        MetricTrees.tree(
            "classDeclaration",
            1,
            1,
            MetricTrees.created("ArrayList<String>"),
            MetricTrees.created("java.util.Map"),
            MetricTrees.created("Foo"));

    assertThat(
            new ClassCouplingCheck(Set.of(CREATED_NAME), Set.of("Map"), LOW_LIMIT, MESSAGE)
                .matches(klass))
        .hasSize(LOW_LIMIT);
    assertThat(
            new ClassCouplingCheck(Set.of(CREATED_NAME), Set.of("Map"), COUPLING_HIGH, MESSAGE)
                .matches(klass))
        .isEmpty();
  }

  @Test
  void anonymousLinesMeasureOnlyTheBodyOfACreation() {
    final SyntaxNode creation =
        MetricTrees.tree(
            "classCreatorRest",
            1,
            CREATION_LAST_LINE,
            MetricTrees.tree("classBody", 1, BODY_LINES));

    assertThat(new AnonymousLinesCheck(ANON_LIMIT, MESSAGE).matches(creation)).hasSize(LOW_LIMIT);
    assertThat(new AnonymousLinesCheck(BODY_LINES, MESSAGE).matches(creation)).isEmpty();
  }
}
