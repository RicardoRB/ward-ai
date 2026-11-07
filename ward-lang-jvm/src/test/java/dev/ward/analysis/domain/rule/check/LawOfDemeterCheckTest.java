package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class LawOfDemeterCheckTest extends ClassStatementTrees {

  private static final String MESSAGE = "{value}";
  private static final String RUN_METHOD = "run";
  private static final String ANOTHER_METHOD = "again";
  private static final String FIELD_ACCESS = "field";
  private static final String THIS = "this";

  @Test
  void reportsCallOnTheResultOfACallOnAForeignValue() {
    final SyntaxNode root =
        compilationUnit(classNamed(CLASS_NAME, member(method(RUN_METHOD, 0, 0, chainOn(FOREIGN)))));

    assertThat(lawOfDemeter(root, typesOf(OTHER))).containsExactly(display(OTHER));
  }

  @Test
  void skipsLawOfDemeterOnThisAndUnknownTypesAndNestedChains() {
    final SyntaxNode onThis =
        compilationUnit(classNamed(CLASS_NAME, member(method(RUN_METHOD, 0, 0, chainOn(THIS)))));
    final SyntaxNode unknown =
        compilationUnit(classNamed(CLASS_NAME, member(method(RUN_METHOD, 0, 0, chainOn(FOREIGN)))));
    final SyntaxNode nested =
        compilationUnit(classNamed(CLASS_NAME, member(method(RUN_METHOD, 0, 0, nestedChain()))));

    assertThat(lawOfDemeter(onThis, typesOf(OTHER))).isEmpty();
    assertThat(lawOfDemeter(unknown, UnitTypeFacts.none())).isEmpty();
    assertThat(lawOfDemeter(nested, typesOf(OTHER))).containsExactly(display(OTHER));
  }

  @Test
  void skipsLawOfDemeterOnAValueOfTheOwnClass() {
    final SyntaxNode root =
        compilationUnit(classNamed(CLASS_NAME, member(method(RUN_METHOD, 0, 0, chainOn(FOREIGN)))));

    assertThat(lawOfDemeter(root, typesOf(OWN))).isEmpty();
  }

  @Test
  void skipsLawOfDemeterOnFieldAccessesAndNonExpressionReceivers() {
    final SyntaxNode fieldChain =
        node(
            EXPRESSION,
            LINE,
            COLUMN,
            node(
                EXPRESSION,
                LINE,
                COLUMN,
                node(EXPRESSION, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, FOREIGN, LINE, COLUMN)),
                leaf(DOT_KIND, DOT, LINE, COLUMN),
                ident(FIELD_ACCESS)),
            leaf(DOT_KIND, DOT, LINE, COLUMN),
            node(METHOD_CALL, LINE, COLUMN, ident(USE)));
    final SyntaxNode notAnExpression =
        node(
            EXPRESSION,
            LINE,
            COLUMN,
            leaf(TOKEN_IDENTIFIER, FOREIGN, LINE, COLUMN),
            leaf(DOT_KIND, DOT, LINE, COLUMN),
            node(METHOD_CALL, LINE, COLUMN, ident(USE)));
    final SyntaxNode root =
        compilationUnit(
            classNamed(
                CLASS_NAME,
                member(method(RUN_METHOD, 0, 0, fieldChain)),
                member(method(ANOTHER_METHOD, 0, 0, notAnExpression))));

    assertThat(lawOfDemeter(root, typesOf(OTHER))).isEmpty();
  }

  private static String display(final String type) {
    return TypeRef.of(type).display();
  }

  private static List<String> lawOfDemeter(final SyntaxNode root, final UnitTypeFacts types) {
    return Trees.messages(new LawOfDemeterCheck(MESSAGE).matches(root, null, null, types, null));
  }
}
