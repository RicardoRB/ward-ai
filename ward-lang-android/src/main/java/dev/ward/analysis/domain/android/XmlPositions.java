package dev.ward.analysis.domain.android;

import dev.ward.analysis.domain.ir.Span;
import java.util.Arrays;

/** Turns character offsets of one text into 1-based lines and columns. */
final class XmlPositions {

  private final int[] lineStarts;
  private final int length;

  XmlPositions(final String text) {
    this.length = text.length();
    final int[] starts = new int[length + 1];
    int count = 1;
    for (int index = 0; index < length; index++) {
      if (endsLine(text, index)) {
        starts[count] = index + 1;
        count++;
      }
    }
    this.lineStarts = Arrays.copyOf(starts, count);
  }

  private static boolean endsLine(final String text, final int index) {
    final char current = text.charAt(index);
    final boolean lastOfPair = index + 1 < text.length() && text.charAt(index + 1) == '\n';
    return current == '\n' || current == '\r' && !lastOfPair;
  }

  int line(final int offset) {
    final int found = Arrays.binarySearch(lineStarts, Math.min(offset, length));
    return (found >= 0 ? found : -found - 2) + 1;
  }

  int column(final int offset) {
    return Math.min(offset, length) - lineStarts[line(offset) - 1] + 1;
  }

  Span span(final int start, final int end) {
    return new Span(line(start), column(start), line(end), column(end), start, end);
  }
}
