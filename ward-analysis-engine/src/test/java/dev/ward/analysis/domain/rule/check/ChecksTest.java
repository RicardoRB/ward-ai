package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.Measured;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChecksTest {

  private static final String IF = "'if'";
  private static final String AND = "'&&'";
  private static final String METHOD = "methodDeclaration";

  @Test
  void linesCheckFailsOnlyAboveTheLimit() {
    final SyntaxNode body = Trees.node("methodBody", 0, Trees.SOURCE_LENGTH, 61);
    final LinesCheck check =
        new LinesCheck(Set.of("methodBody"), Trees.METHOD_LIMIT, "{value} > {max}");

    assertThat(Trees.messages(check.matches(body))).containsExactly("61 > 60");
    assertThat(check.matches(Trees.node("methodBody", 0, Trees.SOURCE_LENGTH, Trees.METHOD_LIMIT)))
        .isEmpty();
  }

  @Test
  void descendantCountAddsTheBaseAndFailsAboveTheLimit() {
    final SyntaxNode method =
        Trees.node(
            METHOD,
            0,
            Trees.DECLARATION_END,
            1,
            Trees.leaf(IF, "if", 0),
            Trees.leaf("'for'", "for", 4),
            Trees.leaf(IF, "if", 8));

    assertThat(
            Trees.messages(
                new DescendantCountCheck(
                        Set.of(METHOD),
                        CountedKinds.of(Set.of(IF, "'for'")),
                        1,
                        2,
                        "{value} > {max}")
                    .matches(method)))
        .containsExactly("4 > 2");
    assertThat(
            new DescendantCountCheck(
                    Set.of(METHOD),
                    CountedKinds.of(Set.of(IF, "'for'")),
                    1,
                    Trees.DESCENDANT_LIMIT,
                    "{value}")
                .matches(method))
        .isEmpty();
  }

  @Test
  void descendantCountMeasuresEveryDeclarationEvenWithinTheLimit() {
    final SyntaxNode method =
        Trees.node(METHOD, 0, Trees.DECLARATION_END, 1, Trees.leaf(IF, "if", 0));
    final DescendantCountCheck check =
        new DescendantCountCheck(
            Set.of(METHOD), CountedKinds.of(Set.of(IF)), 1, Trees.DESCENDANT_LIMIT, "{value}");

    assertThat(check.measures(method)).extracting(Measured::value).containsExactly(2);
    assertThat(check.matches(method)).isEmpty();
  }

  @Test
  void aCheckThatMeasuresNothingReturnsNoMeasurements() {
    final SyntaxNode body = Trees.node("methodBody", 0, Trees.SOURCE_LENGTH, 61);

    assertThat(new LinesCheck(Set.of("methodBody"), 1, "{value}").measures(body)).isEmpty();
  }

  @Test
  void cognitiveComplexityIncreasesWithNesting() {
    final SyntaxNode inner = Trees.node("statement", 6, 20, 1, Trees.leaf(IF, "if", 6));
    final SyntaxNode outer =
        Trees.node(
            "ifStatement", 0, 20, 1, Trees.leaf(IF, "if", 0), Trees.leaf(AND, "&&", 3), inner);
    final CognitiveComplexityCheck check =
        new CognitiveComplexityCheck(
            Set.of("ifStatement"),
            new CognitiveTokens(CountedKinds.of(Set.of(IF)), Set.of(AND), Set.of()),
            3,
            "{value} > {max}");

    // The outer if adds 1, the && adds 1, and the nested if adds 1 plus its nesting of 1.
    assertThat(Trees.messages(check.matches(outer))).containsExactly("4 > 3");
  }

  @Test
  void textPatternChecksTheNameOfEachDeclaration() {
    final SyntaxNode declaration =
        Trees.node(
            "classDeclaration",
            0,
            20,
            1,
            Trees.leaf("CLASS", "class", 0),
            Trees.leaf(Trees.IDENTIFIER, "Order_Item", 6));
    final TextPatternCheck check =
        new TextPatternCheck(
            Trees.filter(Set.of("classDeclaration"), Optional.empty(), Optional.empty()),
            Trees.IDENTIFIER,
            Set.of(),
            "[A-Z][a-zA-Z0-9]*",
            "bad name {value}");

    assertThat(Trees.messages(check.matches(declaration))).containsExactly("bad name Order_Item");
  }

  @Test
  void textPatternSkipsDeclarationsWithTheExcludedModifier() {
    final SyntaxNode field =
        Trees.node(
            "classBodyDeclaration",
            0,
            25,
            1,
            Trees.node("modifier", 0, 6, 1, Trees.leaf("'static'", "static", 0)),
            Trees.node("fieldDeclaration", 7, 25, 1, Trees.leaf(Trees.IDENTIFIER, "MAX_SIZE", 14)));
    final TextPatternCheck check =
        new TextPatternCheck(
            Trees.filter(Set.of("fieldDeclaration"), Optional.empty(), Optional.of("static")),
            Trees.IDENTIFIER,
            Set.of(),
            "[a-z][a-zA-Z0-9]*",
            "{value}");

    assertThat(check.matches(Trees.unit(field))).isEmpty();
  }

  @Test
  void textPatternRejectsMalformedRegularExpression() {
    assertThatThrownBy(
            () ->
                new TextPatternCheck(
                    Trees.filter(Set.of("classDeclaration"), Optional.empty(), Optional.empty()),
                    Trees.IDENTIFIER,
                    Set.of(),
                    "[unclosed",
                    "{value}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void descendantCountSkipsAWhileThatClosesADo() {
    final SyntaxNode loop =
        Trees.node(
            "doStatement",
            0,
            20,
            1,
            Trees.leaf("'do'", "do", 0),
            Trees.leaf("'while'", "while", 12));

    assertThat(
            new DescendantCountCheck(
                    Set.of("doStatement"),
                    new CountedKinds(Set.of("'while'"), Map.of(), Map.of("'while'", "'do'")),
                    0,
                    0,
                    "{value}")
                .matches(loop))
        .isEmpty();
    assertThat(
            Trees.messages(
                new DescendantCountCheck(
                        Set.of("doStatement"), CountedKinds.of(Set.of("'while'")), 0, 0, "{value}")
                    .matches(loop)))
        .containsExactly("1");
  }
}
