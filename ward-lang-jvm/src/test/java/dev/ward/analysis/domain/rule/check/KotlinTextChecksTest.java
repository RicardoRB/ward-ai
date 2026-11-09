package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin file-text checks over small texts. */
class KotlinTextChecksTest {

  private static final String MESSAGE = "m";

  private static List<RuleMatch> newLine(final String text) {
    return new KotlinNewLineAtEndOfFileCheck(MESSAGE)
        .matches(root(), FlowView.NONE, unit(text));
  }

  private static List<RuleMatch> trailing(final String text) {
    return new KotlinTrailingWhitespaceCheck(MESSAGE)
        .matches(root(), FlowView.NONE, unit(text));
  }

  private static SourceUnit unit(final String text) {
    return new SourceUnit("src/Main.kt", AnalysisLanguage.KOTLIN, text);
  }

  private static SyntaxNode root() {
    return new SyntaxNode("kotlinFile", new Span(1, 1, 1, 1, 0, 0), Map.of(), List.of());
  }

  @Test
  void reportsAFileThatEndsWithoutLineFeed() {
    assertThat(newLine("fun work() = 1")).hasSize(1);
  }

  @Test
  void acceptsAFileThatEndsWithLineFeedOrIsEmpty() {
    assertThat(newLine("fun work() = 1\n")).isEmpty();
    assertThat(newLine("")).isEmpty();
  }

  @Test
  void reportsTheTrailingBlanksOfEachLine() {
    final List<RuleMatch> found = trailing("a \nb\t\nc\n");

    assertThat(found).hasSize(2);
    assertThat(found.get(0).span().startLine()).isEqualTo(1);
    assertThat(found.get(0).span().startColumn()).isEqualTo(2);
    assertThat(found.get(1).span().startLine()).isEqualTo(2);
    assertThat(found.get(1).span().startColumn()).isEqualTo(2);
  }

  @Test
  void reportsALastLineWithoutLineFeedAndBlankOnlyLines() {
    assertThat(trailing("x\n   ")).hasSize(1);
    assertThat(trailing("x\n  \ny\n")).hasSize(1);
  }

  @Test
  void treatsACarriageReturnAsLineEndNotBlank() {
    assertThat(trailing("x\r\ny\r\n")).isEmpty();
    assertThat(trailing("x \r\n")).hasSize(1);
  }

  @Test
  void acceptsLinesWithoutTrailingBlanks() {
    assertThat(trailing("val a = 1\n  val b = 2\n")).isEmpty();
  }
}
