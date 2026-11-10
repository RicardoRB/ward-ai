package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SpanTest {

  private static final int START_LINE = 2;
  private static final int START_COLUMN = 3;
  private static final int END_COLUMN = 9;
  private static final int START_OFFSET = 10;
  private static final int END_OFFSET = 16;
  private static final int LATER_LINE = 3;
  private static final int LATER_COLUMN = 5;
  private static final int EARLIER_COLUMN = 4;
  private static final int BAD_OFFSET = 5;
  private static final int EARLIER_OFFSET = 4;

  @Test
  void keepsPositionsAndOffsets() {
    final Span span =
        new Span(START_LINE, START_COLUMN, START_LINE, END_COLUMN, START_OFFSET, END_OFFSET);

    assertThat(span.startLine()).isEqualTo(START_LINE);
    assertThat(span.startColumn()).isEqualTo(START_COLUMN);
    assertThat(span.endColumn()).isEqualTo(END_COLUMN);
    assertThat(span.startOffset()).isEqualTo(START_OFFSET);
    assertThat(span.endOffset()).isEqualTo(END_OFFSET);
  }

  @Test
  void rejectsNonPositiveLine() {
    assertThatThrownBy(() -> new Span(0, 1, 1, 1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveColumn() {
    assertThatThrownBy(() -> new Span(1, 1, 1, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsEndLineBeforeStartLine() {
    assertThatThrownBy(() -> new Span(LATER_LINE, 1, START_LINE, 1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsEndColumnBeforeStartColumnOnSameLine() {
    assertThatThrownBy(() -> new Span(1, LATER_COLUMN, 1, EARLIER_COLUMN, 0, START_LINE + 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsEndOffsetBeforeStartOffset() {
    assertThatThrownBy(() -> new Span(1, 1, 1, START_COLUMN, BAD_OFFSET, EARLIER_OFFSET))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNegativeOffset() {
    assertThatThrownBy(() -> new Span(1, 1, 1, START_COLUMN, -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
