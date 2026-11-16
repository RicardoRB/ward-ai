package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocSummaryCheckTest {
  private static final String COMMENT = "comment";
  private static final String UNIT = "unit";
  private static final String FUNCTION = "func";
  private static final String MESSAGE = "no period";

  private final TreeLayout gen = new TreeLayout();

  private List<RuleMatch> matches(final SyntaxNode root) {
    return new DocSummaryCheck(MESSAGE).matches(root);
  }

  @Test
  void aSentenceThatEndsOnTheSecondLineOfTheRunPasses() {
    final SyntaxNode root =
        gen.node(
            UNIT,
            gen.leaf(COMMENT, "/// Maps a value from one range"),
            gen.leaf(COMMENT, "/// into another one. The rest"),
            gen.leaf(COMMENT, "///"),
            gen.leaf(COMMENT, "/// - Returns: a value"),
            gen.leaf(FUNCTION, "f")
        );

    assertThat(matches(root)).isEmpty();
  }

  @Test
  void aFirstParagraphWithoutAPeriodIsReportedAtItsFirstLine() {
    final SyntaxNode first = gen.leaf(COMMENT, "/// Maps a value");
    final SyntaxNode root =
        gen.node(
            UNIT,
            first,
            gen.leaf(COMMENT, "/// - Returns: a value."),
            gen.leaf(FUNCTION, "f"),
            gen.node("body", gen.leaf(COMMENT, "/** Loads the value */"), gen.leaf(FUNCTION, "g"))
        );

    final List<RuleMatch> found = matches(root);

    assertThat(TreeLayout.messages(found)).containsExactly(MESSAGE, MESSAGE);
    assertThat(found.get(0).span()).isEqualTo(first.span());
  }

  @Test
  void aPlainCommentAndADocumentationOfOnlyTagsAreNotChecked() {
    final SyntaxNode root =
        gen.node(
            UNIT,
            gen.leaf(COMMENT, "// MARK: Section"),
            gen.leaf(FUNCTION, "f"),
            gen.leaf(COMMENT, "/// - Parameter a: first"),
            gen.leaf(FUNCTION, "g"),
            gen.leaf(COMMENT, "/// Ends the run!")
        );

    assertThat(matches(root)).isEmpty();
  }
}
