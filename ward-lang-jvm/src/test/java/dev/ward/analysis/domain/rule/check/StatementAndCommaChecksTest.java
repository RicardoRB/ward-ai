package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The statement-per-line and trailing-comma checks on small trees with explicit lines. */
class StatementAndCommaChecksTest extends TreeBuilders {

  private static final String SEMICOLON = "';'";
  private static final String COMMA = "','";
  private static final String OPEN = "'{'";
  private static final String CLOSE = "'}'";
  private static final String RESOURCE = "resource";

  @Test
  void twoStatementsOnOneLineAreReportedAtTheSecond() {
    final SyntaxNode block = n("block", token(SEMICOLON, ";", 1), token(SEMICOLON, ";", 1));
    assertThat(new OneStatementPerLineCheck(false, MESSAGE).check(unit(block), FILE)).hasSize(1);
    final SyntaxNode apart = n("block", token(SEMICOLON, ";", 1), token(SEMICOLON, ";", 2));
    assertThat(new OneStatementPerLineCheck(false, MESSAGE).check(unit(apart), FILE)).isEmpty();
  }

  @Test
  void forHeadersAndEmptyMembersDoNotCountAsStatements() {
    final SyntaxNode loop =
        n(
            "block",
            n("forControl", token(SEMICOLON, ";", 1), token(SEMICOLON, ";", 1)),
            token(SEMICOLON, ";", 1));
    assertThat(new OneStatementPerLineCheck(false, MESSAGE).check(unit(loop), FILE)).isEmpty();
    final SyntaxNode members =
        n("classBodyDeclaration", token(SEMICOLON, ";", 1), token(SEMICOLON, ";", 1));
    assertThat(new OneStatementPerLineCheck(false, MESSAGE).check(unit(members), FILE)).isEmpty();
  }

  @Test
  void tryResourcesCountOnlyWhenTheOptionIsOn() {
    final SyntaxNode resources =
        n(
            "resourceSpecification",
            n(RESOURCE, token(RESOURCE, "a", 1)),
            token(SEMICOLON, ";", 1),
            n(RESOURCE, token(RESOURCE, "b", 1)));
    assertThat(new OneStatementPerLineCheck(true, MESSAGE).check(unit(resources), FILE)).hasSize(1);
    assertThat(new OneStatementPerLineCheck(false, MESSAGE).check(unit(resources), FILE)).isEmpty();
  }

  @Test
  void closingBraceOnALaterLineNeedsATrailingComma() {
    final SyntaxNode multiline = array(token("INT", "1", 1), token(CLOSE, "}", 2));
    assertThat(new ArrayTrailingCommaCheck(false, MESSAGE).check(unit(multiline), FILE)).hasSize(1);
    final SyntaxNode sameLine = array(token("INT", "1", 1), token(CLOSE, "}", 1));
    assertThat(new ArrayTrailingCommaCheck(false, MESSAGE).check(unit(sameLine), FILE)).isEmpty();
    assertThat(new ArrayTrailingCommaCheck(true, MESSAGE).check(unit(sameLine), FILE)).hasSize(1);
  }

  @Test
  void arrayWithATrailingCommaOrNoElementsIsNotReported() {
    final SyntaxNode comma =
        array(token("INT", "1", 1), token(COMMA, ",", 1), token(CLOSE, "}", 2));
    assertThat(new ArrayTrailingCommaCheck(false, MESSAGE).check(unit(comma), FILE)).isEmpty();
    final SyntaxNode empty = n("arrayInitializer", token(OPEN, "{", 1), token(CLOSE, "}", 2));
    assertThat(new ArrayTrailingCommaCheck(true, MESSAGE).check(unit(empty), FILE)).isEmpty();
    final SyntaxNode bare = n("arrayInitializer", token(OPEN, "{", 1), token("INT", "1", 1));
    assertThat(new ArrayTrailingCommaCheck(true, MESSAGE).check(unit(bare), FILE)).isEmpty();
  }

  @Test
  void enumTrailingCommaIsReported() {
    final SyntaxNode with = n("enumDeclaration", t(COMMA, ","));
    assertThat(new NoEnumTrailingCommaCheck(MESSAGE).check(unit(with), FILE)).hasSize(1);
    final SyntaxNode without = n("enumDeclaration", t(TOKEN_IDENTIFIER, "A"));
    assertThat(new NoEnumTrailingCommaCheck(MESSAGE).check(unit(without), FILE)).isEmpty();
  }

  private static SyntaxNode array(final SyntaxNode... elements) {
    final SyntaxNode[] children = new SyntaxNode[elements.length + 1];
    children[0] = token(OPEN, "{", 1);
    System.arraycopy(elements, 0, children, 1, elements.length);
    return n("arrayInitializer", children);
  }

  private static SyntaxNode token(final String kind, final String text, final int line) {
    final Span span = new Span(line, 1, line, 1 + text.length(), 0, text.length());
    return new SyntaxNode(kind, span, Map.of("text", text), List.of());
  }
}
