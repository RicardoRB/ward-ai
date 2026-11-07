package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The text block format check on tokens placed in a small source text. */
class TextBlockFormatCheckTest extends TreeBuilders {

  private static final String QUOTES = "\"\"\"";
  private static final String BLOCK = QUOTES + "\n  hi\n  " + QUOTES;

  @Test
  void openingQuotesAfterCodeAreReported() {
    final String source = "String s = " + BLOCK + ";";
    assertThat(run(source, source.indexOf(QUOTES), BLOCK)).hasSize(1);
  }

  @Test
  void openingQuotesThatStartTheLineAreNotReported() {
    final String source = "String s =\n  " + BLOCK + ";";
    assertThat(run(source, source.indexOf(QUOTES), BLOCK)).isEmpty();
  }

  @Test
  void closingQuotesAfterContentAreReported() {
    final String block = QUOTES + "\n  hi" + QUOTES;
    assertThat(run(block, 0, block)).hasSize(1);
  }

  @Test
  void aBareTreeWithoutSourceTextOnlyChecksTheClosingQuotes() {
    assertThat(
            new TextBlockFormatCheck(MESSAGE).check(unit(Trees.leaf("TEXT_BLOCK", BLOCK, 0)), FILE))
        .isEmpty();
  }

  private static List<RuleMatch> run(final String source, final int offset, final String block) {
    final SyntaxNode token = Trees.leaf("TEXT_BLOCK", block, offset);
    return new TextBlockFormatCheck(MESSAGE).check(unit(token), new SourceFile("", source));
  }
}
