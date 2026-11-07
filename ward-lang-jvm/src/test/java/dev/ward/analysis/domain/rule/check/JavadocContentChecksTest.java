package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Javadoc position, summary, paragraph and method checks on small trees. */
class JavadocContentChecksTest extends TreeBuilders {

  private static final int COMMENT_OFFSET = 2;
  private static final int MEMBER_OFFSET = 30;
  private static final int NAME_OFFSET = 35;
  private static final int PARAMETER_OFFSET = 40;
  private static final String FIRST = "/**\n   * First.\n   */";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String VOID = "void";

  @Test
  void commentBeforeAMemberIsInPlaceAndOneInABodyIsNot() {
    final SyntaxNode placed = n(CLASS_BODY, comment(FIRST), n(CLASS_BODY_DECLARATION));
    assertThat(position(placed)).isEmpty();
    final SyntaxNode misplaced = n(BLOCK, comment(FIRST), n(BLOCK_STATEMENT));
    assertThat(position(misplaced)).hasSize(1);
    final SyntaxNode trailing = n(CLASS_BODY, n(CLASS_BODY_DECLARATION), comment(FIRST));
    assertThat(position(trailing)).hasSize(1);
    final SyntaxNode stacked =
        n(CLASS_BODY, comment(FIRST), comment("/* note */"), n(CLASS_BODY_DECLARATION));
    assertThat(position(stacked)).isEmpty();
  }

  @Test
  void summaryNeedsAnEndingPeriodAndMustExist() {
    assertThat(summary("/** Does it. */")).isEmpty();
    assertThat(summary("/**\n   * No period\n   */")).hasSize(1);
    assertThat(summary("/**\n   * @return it\n   */")).hasSize(1);
    assertThat(summary("/** {@inheritDoc} */")).isEmpty();
    assertThat(summary("/** {@return it} */")).isEmpty();
    assertThat(summary("/**\n   * Does it.\n   *\n   * @return it\n   */")).isEmpty();
  }

  @Test
  void paragraphTagsFollowEmptyLinesAndEmptyLinesStartParagraphTags() {
    assertThat(paragraph("/**\n   * One.\n   *\n   * <p>Two.\n   */")).isEmpty();
    assertThat(paragraph("/**\n   * One.\n   * <p>Two.\n   */")).hasSize(1);
    assertThat(paragraph("/**\n   * One.\n   *\n   * Two.\n   */")).hasSize(1);
    assertThat(paragraph("/**\n   * One.\n   *\n   * <ul>\n   */")).isEmpty();
    assertThat(paragraph("/**\n   * One.\n   *\n   * @param a it\n   */")).isEmpty();
  }

  @Test
  void methodTagsMustMatchTheSignature() {
    assertThat(method("/**\n   * Text.\n   *\n   * @param a it\n   */", VOID, "a")).isEmpty();
    assertThat(method("/**\n   * Text.\n   */", VOID, "a")).hasSize(1);
    assertThat(method("/**\n   * Text.\n   *\n   * @param b it\n   */", VOID, "a")).hasSize(2);
    assertThat(method("/**\n   * Text.\n   */", "int")).hasSize(1);
    assertThat(method("/**\n   * Text.\n   *\n   * @return it\n   */", "int")).isEmpty();
    assertThat(method("/**\n   * {@inheritDoc}\n   */", "int")).isEmpty();
    assertThat(method("/**\n   * Text.\n   *\n   * @param <T> it\n   */", VOID)).isEmpty();
  }

  private static List<RuleMatch> position(final SyntaxNode parent) {
    return new InvalidJavadocPositionCheck(MESSAGE).check(unit(parent), FILE);
  }

  private static List<RuleMatch> summary(final String text) {
    return new JavadocSummaryCheck(MESSAGE).check(unit(comment(text)), FILE);
  }

  private static List<RuleMatch> paragraph(final String text) {
    return new JavadocParagraphCheck(MESSAGE).check(unit(comment(text)), FILE);
  }

  private static List<RuleMatch> method(
      final String text, final String returnType, final String... parameters) {
    final SyntaxNode[] formals = new SyntaxNode[parameters.length];
    for (int index = 0; index < parameters.length; index++) {
      formals[index] =
          n(
              FORMAL_PARAMETER,
              Trees.leaf("int", "int", PARAMETER_OFFSET),
              n(PARAMETER_ID, Trees.leaf(TOKEN_IDENTIFIER, parameters[index], PARAMETER_OFFSET)));
    }
    final SyntaxNode method =
        Trees.node(
            METHOD_DECLARATION,
            MEMBER_OFFSET,
            MEMBER_OFFSET + MEMBER_OFFSET,
            1,
            n("typeTypeOrVoid", Trees.leaf(returnType, returnType, MEMBER_OFFSET)),
            n(IDENTIFIER, Trees.leaf(TOKEN_IDENTIFIER, "run", NAME_OFFSET)),
            n(FORMAL_PARAMETERS, formals));
    final SyntaxNode wrapper =
        Trees.node(CLASS_BODY_DECLARATION, MEMBER_OFFSET, MEMBER_OFFSET + MEMBER_OFFSET, 1, method);
    final SyntaxNode body = n(CLASS_BODY, Trees.leaf("'{'", "{", 0), comment(text), wrapper);
    return new JavadocMethodTagsCheck(MESSAGE).check(unit(body), FILE);
  }

  private static SyntaxNode comment(final String text) {
    return Trees.leaf("comment", text, COMMENT_OFFSET);
  }
}
