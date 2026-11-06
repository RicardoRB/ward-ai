package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Runs the Java size and metric checks over small hand-made syntax trees. */
class JavaMetricChecksTest {

  private static final String MESSAGE = "{value} over {max}";

  @Test
  void lineLengthReportsOnlyLinesOverTheLimitThatTheIgnorePatternDoesNotFind() {
    final String text = "short\n" + "x".repeat(30) + "\n// https://" + "y".repeat(30);
    final List<RuleMatch> matches =
        new LineLengthCheck(20, 8, "http", MESSAGE)
            .check(leafFree(), new SourceFile("F.java", text));

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span().startLine()).isEqualTo(2);
  }

  @Test
  void lineLengthExpandsTabsToTheTabWidth() {
    final String text = "\tx";
    assertThat(
            new LineLengthCheck(4, 4, "^$", MESSAGE)
                .check(leafFree(), new SourceFile("F.java", text)))
        .hasSize(1);
    assertThat(
            new LineLengthCheck(5, 4, "^$", MESSAGE)
                .check(leafFree(), new SourceFile("F.java", text)))
        .isEmpty();
  }

  @Test
  void linesWithoutEmptyLinesCountOnlyLinesThatStartCode() {
    final SyntaxNode body = tree("methodBody", 1, 5, token("'{'", 1), token("'}'", 5));

    assertThat(new LinesCheck(Set.of("methodBody"), 2, MESSAGE, false).matches(body)).isEmpty();
    assertThat(new LinesCheck(Set.of("methodBody"), 2, MESSAGE, true).matches(body)).hasSize(1);
  }

  @Test
  void booleanOperatorsCountOnlyOutermostChains() {
    final SyntaxNode chain =
        tree(
            "expression",
            1,
            1,
            tree("expression", 1, 1),
            token("'&&'", 1),
            tree(
                "expression",
                1,
                1,
                tree("expression", 1, 1),
                token("'||'", 1),
                tree("expression", 1, 1)));

    assertThat(new BooleanOperatorCheck(1, MESSAGE).matches(chain)).hasSize(1);
    assertThat(new BooleanOperatorCheck(2, MESSAGE).matches(chain)).isEmpty();
  }

  @Test
  void npathMultipliesSequentialIfStatements() {
    final SyntaxNode method =
        tree(
            "methodDeclaration",
            1,
            3,
            tree("methodBody", 1, 3, tree("block", 1, 3, ifBlock(), ifBlock())));

    assertThat(new NPathCheck(3, MESSAGE).matches(method)).hasSize(1);
    assertThat(new NPathCheck(4, MESSAGE).matches(method)).isEmpty();
  }

  @Test
  void ncssCountsStatementsOfAMethodAndAddsOne() {
    final SyntaxNode method = tree("methodDeclaration", 1, 4, statement(), statement());

    assertThat(new NcssCheck(2, 100, 100, MESSAGE).matches(method)).hasSize(1);
    assertThat(new NcssCheck(3, 100, 100, MESSAGE).matches(method)).isEmpty();
  }

  @Test
  void methodCountReportsTotalAndEachVisibilityOverItsLimit() {
    final SyntaxNode body =
        tree("classBody", 1, 4, member(token("private", 1)), member(token("public", 2)));

    final MethodCountCheck.Limits strict = new MethodCountCheck.Limits(1, 0, 5, 5, 5);
    assertThat(new MethodCountCheck(strict, MESSAGE).matches(body)).hasSize(2);
    final MethodCountCheck.Limits loose = new MethodCountCheck.Limits(2, 1, 5, 5, 5);
    assertThat(new MethodCountCheck(loose, MESSAGE).matches(body)).isEmpty();
  }

  @Test
  void classCouplingCountsDistinctSimpleNamesThatAreNotExcluded() {
    final SyntaxNode klass =
        tree(
            "classDeclaration",
            1,
            1,
            created("ArrayList<String>"),
            created("java.util.Map"),
            created("Foo"));

    assertThat(
            new ClassCouplingCheck(Set.of("createdName"), Set.of("Map"), 1, MESSAGE).matches(klass))
        .hasSize(1);
    assertThat(
            new ClassCouplingCheck(Set.of("createdName"), Set.of("Map"), 2, MESSAGE).matches(klass))
        .isEmpty();
  }

  @Test
  void anonymousLinesMeasureOnlyTheBodyOfACreation() {
    final SyntaxNode creation = tree("classCreatorRest", 1, 12, tree("classBody", 1, 10));

    assertThat(new AnonymousLinesCheck(5, MESSAGE).matches(creation)).hasSize(1);
    assertThat(new AnonymousLinesCheck(10, MESSAGE).matches(creation)).isEmpty();
  }

  private static SyntaxNode leafFree() {
    return tree("compilationUnit", 1, 1);
  }

  private static SyntaxNode ifBlock() {
    return tree(
        "blockStatement",
        1,
        1,
        tree(
            "statement",
            1,
            1,
            token("'if'", 1),
            tree("expression", 1, 1),
            tree("statement", 1, 1, tree("expression", 1, 1))));
  }

  private static SyntaxNode statement() {
    return tree("statement", 1, 1, tree("expression", 1, 1));
  }

  private static SyntaxNode member(final SyntaxNode modifier) {
    return tree(
        "classBodyDeclaration",
        1,
        1,
        tree("modifier", 1, 1, modifier),
        tree("memberDeclaration", 1, 1, tree("methodDeclaration", 1, 1)));
  }

  private static SyntaxNode created(final String text) {
    return new SyntaxNode(
        "createdName", span(1, 1), Map.of(), List.of(token("identifier", text, 1)));
  }

  private static SyntaxNode token(final String text, final int line) {
    return token(text, text, line);
  }

  private static SyntaxNode token(final String kind, final String text, final int line) {
    return new SyntaxNode(kind, span(line, line), Map.of("text", text), List.of());
  }

  private static SyntaxNode tree(
      final String kind, final int startLine, final int endLine, final SyntaxNode... children) {
    return new SyntaxNode(kind, span(startLine, endLine), Map.of(), List.of(children));
  }

  private static Span span(final int startLine, final int endLine) {
    return new Span(startLine, 1, endLine, 1, 0, 0);
  }
}
