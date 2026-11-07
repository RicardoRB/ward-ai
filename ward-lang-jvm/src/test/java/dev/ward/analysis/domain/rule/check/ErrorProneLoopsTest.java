package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class ErrorProneLoopsTest extends ErrorProneTrees {

  private static final String GET_INSTANCE = "getInstance";
  private static final String FOR_CONTROL = "forControl";
  private static final String EXPRESSION_LIST = "expressionList";

  @Test
  void reportsEveryGetInstanceAfterTheFirst() {
    final SyntaxNode clazz =
        classNamed(
            CLASS_NAME,
            member(method(GET_INSTANCE, 0, 0)),
            member(method(GET_INSTANCE, 1, 0)),
            member(method(GET_INSTANCE, 2, 0)));

    assertThat(run(new SingleMethodSingletonCheck(MESSAGE), clazz)).hasSize(2);
  }

  @Test
  void skipsASingleGetInstance() {
    final SyntaxNode clazz =
        classNamed(CLASS_NAME, member(method(GET_INSTANCE, 0, 0)), member(method("other", 0, 0)));

    assertThat(run(new SingleMethodSingletonCheck(MESSAGE), clazz)).isEmpty();
  }

  private SyntaxNode forLoop(final String variable, final String update, final SyntaxNode... body) {
    final SyntaxNode init =
        node(
            "forInit",
            LINE,
            COLUMN,
            node(
                "localVariableDeclaration",
                LINE,
                COLUMN,
                node(VARIABLE_DECLARATOR_ID, LINE, COLUMN, ident(variable))));
    final SyntaxNode updateList =
        node(
            EXPRESSION_LIST,
            LINE,
            COLUMN,
            leaf(TOKEN_IDENTIFIER, update.split("\\|")[0], LINE, COLUMN),
            leaf("OP", update.split("\\|")[1], LINE, COLUMN));
    return node(
        "statement",
        LINE,
        COLUMN,
        leaf("'for'", "for", LINE, COLUMN),
        node(FOR_CONTROL, LINE, COLUMN, init, leaf("';'", ";", LINE, COLUMN), updateList),
        node(BLOCK, LINE, COLUMN, body));
  }

  @Test
  void reportsInnerLoopThatUpdatesTheOuterVariable() {
    final SyntaxNode inner = forLoop("j", "i|++");
    final SyntaxNode outer = forLoop("i", "i|++", inner);

    assertThat(run(new JumbledIncrementerCheck(MESSAGE), outer)).hasSize(1);
    assertThat(run(new JumbledIncrementerCheck(MESSAGE), forLoop("i", "x|+=", inner))).hasSize(1);
  }

  @Test
  void skipsLoopsThatUpdateTheirOwnVariable() {
    final SyntaxNode outer = forLoop("i", "i|++", forLoop("j", "j|++"));
    final SyntaxNode compound = forLoop("i", "i|--", forLoop("i", "i|+="));
    final SyntaxNode comparison = forLoop("i", "i|++", forLoop("j", "k|=="));
    final SyntaxNode noUpdate =
        node(
            "statement",
            LINE,
            COLUMN,
            node(FOR_CONTROL, LINE, COLUMN, leaf("';'", ";", LINE, COLUMN)));

    assertThat(run(new JumbledIncrementerCheck(MESSAGE), outer)).isEmpty();
    assertThat(run(new JumbledIncrementerCheck(MESSAGE), compound)).isEmpty();
    assertThat(run(new JumbledIncrementerCheck(MESSAGE), comparison)).isEmpty();
    assertThat(run(new JumbledIncrementerCheck(MESSAGE), noUpdate)).isEmpty();
  }
}
