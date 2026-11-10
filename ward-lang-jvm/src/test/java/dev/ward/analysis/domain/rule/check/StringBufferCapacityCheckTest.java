package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The capacity check: a default-sized builder that literal appends fill past its capacity. */
class StringBufferCapacityCheckTest extends TreeBuilders {

  private static final String VARIABLE = "bad";
  private static final String LITERAL = "STRING_LITERAL";
  private static final int CAPACITY = 16;
  private static final int OVER_CAPACITY = 17;
  private static final int PRESIZED = 30;

  private final StringBufferCapacityCheck check = new StringBufferCapacityCheck(MESSAGE);

  @Test
  void aDefaultSizedBuilderWithMoreLiteralCharactersThanItsCapacityIsReported() {
    assertThat(check.check(unit(block(declaration(), appendOf(OVER_CAPACITY))), FILE)).hasSize(1);
  }

  @Test
  void literalCharactersAtTheCapacityAreNotReported() {
    assertThat(check.check(unit(block(declaration(), appendOf(CAPACITY))), FILE)).isEmpty();
  }

  @Test
  void aBuilderWithAnExplicitSizeIsNotReported() {
    final SyntaxNode sized =
        n(
            STATEMENT,
            t(TOKEN_IDENTIFIER, "StringBuilder"),
            t(TOKEN_IDENTIFIER, VARIABLE),
            t(TOKEN_EQUALS, "="),
            t("'new'", "new"),
            t(TOKEN_IDENTIFIER, "StringBuilder"),
            t(TOKEN_OPEN, "("),
            t("DECIMAL_LITERAL", "41"),
            t(TOKEN_CLOSE, ")"),
            t(TOKEN_SEMICOLON, ";"));

    assertThat(check.check(unit(block(sized, appendOf(PRESIZED))), FILE)).isEmpty();
  }

  @Test
  void appendsOfOtherValuesCountAsNothing() {
    final SyntaxNode other =
        n(
            STATEMENT,
            id(VARIABLE),
            t(TOKEN_DOT, "."),
            id("append"),
            t(TOKEN_OPEN, "("),
            t(TOKEN_IDENTIFIER, "value"),
            t(TOKEN_CLOSE, ")"),
            t(TOKEN_SEMICOLON, ";"));

    assertThat(check.check(unit(block(declaration(), other)), FILE)).isEmpty();
  }

  private static SyntaxNode declaration() {
    return n(
        STATEMENT,
        t(TOKEN_IDENTIFIER, "StringBuilder"),
        t(TOKEN_IDENTIFIER, VARIABLE),
        t(TOKEN_EQUALS, "="),
        t("'new'", "new"),
        t(TOKEN_IDENTIFIER, "StringBuilder"),
        t(TOKEN_OPEN, "("),
        t(TOKEN_CLOSE, ")"),
        t(TOKEN_SEMICOLON, ";"));
  }

  private static SyntaxNode appendOf(final int characters) {
    final String literal = "\"" + "x".repeat(characters) + "\"";
    return n(
        STATEMENT,
        id(VARIABLE),
        t(TOKEN_DOT, "."),
        id("append"),
        t(TOKEN_OPEN, "("),
        t(LITERAL, literal),
        t(TOKEN_CLOSE, ")"),
        t(TOKEN_SEMICOLON, ";"));
  }

  private static SyntaxNode block(final SyntaxNode... statements) {
    final SyntaxNode[] children = new SyntaxNode[statements.length + 2];
    children[0] = t("'{'", "{");
    System.arraycopy(statements, 0, children, 1, statements.length);
    children[children.length - 1] = t("'}'", "}");
    return n(BLOCK, children);
  }
}
