package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The Javadoc asterisk and layout checks on small comments placed at column 3. */
class JavadocLayoutChecksTest extends TreeBuilders {

  private static final int OFFSET = 2;
  private static final String ALIGNED = "/**\n   * Text.\n   */";

  @Test
  void asterisksThatAreNotAlignedAreReported() {
    final SyntaxNode comment = comment("/**\n    * Text.\n   */");
    assertThat(asterisk(JavadocAsteriskCheck.Mode.ALIGN, comment)).hasSize(1);
    assertThat(asterisk(JavadocAsteriskCheck.Mode.ALIGN, comment(ALIGNED))).isEmpty();
  }

  @Test
  void rowsWithoutAnAsteriskAreReported() {
    assertThat(asterisk(JavadocAsteriskCheck.Mode.MISSING, comment("/**\n   Text.\n   */")))
        .hasSize(1);
    assertThat(asterisk(JavadocAsteriskCheck.Mode.MISSING, comment("/**\n\n   */"))).isEmpty();
    assertThat(asterisk(JavadocAsteriskCheck.Mode.MISSING, comment(ALIGNED))).isEmpty();
  }

  @Test
  void asterisksNotFollowedByASpaceAreReported() {
    assertThat(asterisk(JavadocAsteriskCheck.Mode.WHITESPACE, comment("/**\n   *Text.\n   */")))
        .hasSize(1);
    assertThat(asterisk(JavadocAsteriskCheck.Mode.WHITESPACE, comment(ALIGNED))).isEmpty();
    assertThat(asterisk(JavadocAsteriskCheck.Mode.WHITESPACE, comment("/**\n   *\n   */")))
        .isEmpty();
  }

  @Test
  void singleLineCommentWithABlockTagIsReported() {
    assertThat(layout(JavadocLayoutCheck.Mode.SINGLE_LINE, comment("/** @return it */")))
        .hasSize(1);
    assertThat(layout(JavadocLayoutCheck.Mode.SINGLE_LINE, comment("/** Uses {@code x}. */")))
        .isEmpty();
    assertThat(layout(JavadocLayoutCheck.Mode.SINGLE_LINE, comment("/**\n   * @return it\n   */")))
        .isEmpty();
  }

  @Test
  void textOnTheOpeningLineOfAMultiLineCommentIsReported() {
    assertThat(
            layout(
                JavadocLayoutCheck.Mode.CONTENT_LOCATION, comment("/** Text.\n   * More.\n   */")))
        .hasSize(1);
    assertThat(layout(JavadocLayoutCheck.Mode.CONTENT_LOCATION, comment(ALIGNED))).isEmpty();
    assertThat(layout(JavadocLayoutCheck.Mode.CONTENT_LOCATION, comment("/** One line. */")))
        .isEmpty();
  }

  @Test
  void firstBlockTagMustFollowAnEmptyLine() {
    final SyntaxNode tight = comment("/**\n   * Text.\n   * @return it\n   */");
    assertThat(layout(JavadocLayoutCheck.Mode.EMPTY_LINE_BEFORE_TAGS, tight)).hasSize(1);
    final SyntaxNode spaced = comment("/**\n   * Text.\n   *\n   * @return it\n   */");
    assertThat(layout(JavadocLayoutCheck.Mode.EMPTY_LINE_BEFORE_TAGS, spaced)).isEmpty();
    final SyntaxNode only = comment("/**\n   * @return it\n   */");
    assertThat(layout(JavadocLayoutCheck.Mode.EMPTY_LINE_BEFORE_TAGS, only)).isEmpty();
    assertThat(layout(JavadocLayoutCheck.Mode.EMPTY_LINE_BEFORE_TAGS, comment(ALIGNED))).isEmpty();
  }

  private static SyntaxNode comment(final String text) {
    return Trees.leaf("comment", text, OFFSET);
  }

  private static java.util.List<dev.ward.analysis.domain.rule.RuleMatch> asterisk(
      final JavadocAsteriskCheck.Mode mode, final SyntaxNode comment) {
    return new JavadocAsteriskCheck(mode, MESSAGE).check(unit(comment), FILE);
  }

  private static java.util.List<dev.ward.analysis.domain.rule.RuleMatch> layout(
      final JavadocLayoutCheck.Mode mode, final SyntaxNode comment) {
    return new JavadocLayoutCheck(mode, MESSAGE).check(unit(comment), FILE);
  }
}
