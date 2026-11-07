package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class LayoutBraceChecksTest {

  private static final String MESSAGE = "message";
  private static final String OPEN = "'{'";
  private static final String CLOSE = "'}'";
  private static final String ELSE = "'else'";
  private static final String COMMA = "','";
  private static final String SEMICOLON = "';'";
  private static final String IDENTIFIER = "IDENTIFIER";
  private static final String IF = "'if'";
  private static final String FIRST_LINE_KIND = "classDeclaration";
  private static final int BLOCK_COLUMN = 3;
  private static final int CLOSE_COLUMN = 10;
  private static final int AFTER_COLUMN = 20;
  private static final int BRACE_LINE = 2;
  private static final int LINE_THREE = 3;

  @Test
  void rightCurlyAloneFlagsAContinuationOnTheSameLine() {
    assertThat(
            TokenTrees.unitMatches(
                new RightCurlyCheck("alone", MESSAGE),
                ifBlock(2, tok(ELSE, "else", 2, AFTER_COLUMN))))
        .hasSize(1);
    assertThat(
            TokenTrees.unitMatches(
                new RightCurlyCheck("alone", MESSAGE),
                ifBlock(2, tok(SEMICOLON, ";", 2, AFTER_COLUMN))))
        .isEmpty();
    assertThat(
            TokenTrees.unitMatches(
                new RightCurlyCheck("alone", MESSAGE),
                ifBlock(2, tok(ELSE, "else", LINE_THREE, AFTER_COLUMN))))
        .isEmpty();
    assertThat(TokenTrees.unitMatches(new RightCurlyCheck("alone", MESSAGE), ifBlock(2))).isEmpty();
  }

  @Test
  void rightCurlySameFlagsAContinuationOnANewLineAndRejectsUnknownOptions() {
    assertThat(
            TokenTrees.unitMatches(
                new RightCurlyCheck("same", MESSAGE),
                ifBlock(2, tok(ELSE, "else", LINE_THREE, AFTER_COLUMN))))
        .hasSize(1);
    assertThat(
            TokenTrees.unitMatches(
                new RightCurlyCheck("same", MESSAGE),
                ifBlock(2, tok(ELSE, "else", 2, AFTER_COLUMN))))
        .isEmpty();
    assertThat(
            TokenTrees.unitMatches(
                new RightCurlyCheck("same", MESSAGE),
                ifBlock(2, tok(IDENTIFIER, "more", LINE_THREE, AFTER_COLUMN))))
        .isEmpty();
    assertThatThrownBy(() -> new RightCurlyCheck("left", MESSAGE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void googleRightCurlyFlagsACloseThatSharesALineWithCodeOnly() {
    final SyntaxNode shared =
        group(
            "statement",
            tok(IF, "if", 1, 0),
            group(
                "block",
                tok(OPEN, "{", 1, BLOCK_COLUMN),
                tok(IDENTIFIER, "x", BRACE_LINE, 6),
                tok(CLOSE, "}", BRACE_LINE, CLOSE_COLUMN)));
    final SyntaxNode empty =
        group(
            "statement",
            tok(IF, "if", 1, 0),
            group("block", tok(OPEN, "{", 1, BLOCK_COLUMN), tok(CLOSE, "}", 1, 4)));
    final SyntaxNode ownLine =
        group(
            "statement",
            tok(IF, "if", 1, 0),
            group(
                "block",
                tok(OPEN, "{", 1, BLOCK_COLUMN),
                tok(IDENTIFIER, "x", BRACE_LINE, 6),
                tok(CLOSE, "}", LINE_THREE, CLOSE_COLUMN)));
    assertThat(TokenTrees.unitMatches(new GoogleRightCurlyCheck(MESSAGE), shared)).hasSize(1);
    assertThat(TokenTrees.unitMatches(new GoogleRightCurlyCheck(MESSAGE), empty)).isEmpty();
    assertThat(TokenTrees.unitMatches(new GoogleRightCurlyCheck(MESSAGE), ownLine)).isEmpty();
  }

  @Test
  void googleRightCurlyFlagsAContinuationOnANewLine() {
    assertThat(
            TokenTrees.unitMatches(
                new GoogleRightCurlyCheck(MESSAGE),
                ifBlock(1, tok(ELSE, "else", BRACE_LINE, AFTER_COLUMN))))
        .hasSize(1);
    assertThat(
            TokenTrees.unitMatches(
                new GoogleRightCurlyCheck(MESSAGE),
                ifBlock(1, tok(COMMA, ",", BRACE_LINE, AFTER_COLUMN))))
        .hasSize(1);
    assertThat(
            TokenTrees.unitMatches(
                new GoogleRightCurlyCheck(MESSAGE),
                ifBlock(1, tok(IDENTIFIER, "other", BRACE_LINE, AFTER_COLUMN))))
        .isEmpty();
    assertThat(
            TokenTrees.unitMatches(
                new GoogleRightCurlyCheck(MESSAGE), ifBlock(1, tok(ELSE, "else", 1, AFTER_COLUMN))))
        .isEmpty();
  }

  @Test
  void leftCurlyFlagsAnOpenBraceOnANewLineExceptInIgnoredParents() {
    final SyntaxNode newLine =
        group(
            FIRST_LINE_KIND, tok(IDENTIFIER, "X", 1, 0), tok(OPEN, "{", BRACE_LINE, CLOSE_COLUMN));
    final SyntaxNode sameLine =
        group(FIRST_LINE_KIND, tok(IDENTIFIER, "X", 1, 0), tok(OPEN, "{", 1, CLOSE_COLUMN));
    assertThat(TokenTrees.unitMatches(new LeftCurlyCheck(true, MESSAGE), newLine)).hasSize(1);
    assertThat(TokenTrees.unitMatches(new LeftCurlyCheck(true, MESSAGE), sameLine)).isEmpty();
    final SyntaxNode enumDeclaration =
        group(
            "enumDeclaration",
            tok(IDENTIFIER, "Color", 1, 0),
            tok(OPEN, "{", BRACE_LINE, CLOSE_COLUMN));
    assertThat(TokenTrees.unitMatches(new LeftCurlyCheck(true, MESSAGE), enumDeclaration))
        .isEmpty();
    assertThat(TokenTrees.unitMatches(new LeftCurlyCheck(false, MESSAGE), enumDeclaration))
        .hasSize(1);
    final SyntaxNode array =
        group(
            "arrayInitializer",
            tok(IDENTIFIER, "x", 1, 0),
            tok(OPEN, "{", BRACE_LINE, CLOSE_COLUMN));
    assertThat(TokenTrees.unitMatches(new LeftCurlyCheck(false, MESSAGE), array)).isEmpty();
    assertThat(
            TokenTrees.unitMatches(
                new LeftCurlyCheck(false, MESSAGE), group(FIRST_LINE_KIND, tok(OPEN, "{", 1, 0))))
        .isEmpty();
  }

  private static SyntaxNode ifBlock(final int closeLine, final SyntaxNode... after) {
    final SyntaxNode statement =
        group(
            "statement",
            tok(IF, "if", 1, 0),
            group(
                "block",
                tok(OPEN, "{", 1, BLOCK_COLUMN),
                tok(CLOSE, "}", closeLine, CLOSE_COLUMN)));
    if (after.length == 0) {
      return statement;
    }
    final SyntaxNode[] children = new SyntaxNode[after.length + 1];
    children[0] = statement;
    System.arraycopy(after, 0, children, 1, after.length);
    return group("program", children);
  }
}
