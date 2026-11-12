package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The text of one document with the mapping from offsets to lines and columns. */
final class YamlSource {

  private static final char BYTE_ORDER_MARK = '﻿';

  private final String text;
  private final int[] lineStarts;
  private final int start;

  YamlSource(final String text) {
    this.text = text;
    this.start = !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? 1 : 0;
    this.lineStarts = lineStarts(text, start);
  }

  String text() {
    return text;
  }

  /** The offset of the first character after a byte-order mark. */
  int start() {
    return start;
  }

  /** The offset where the line of {@code offset} starts. */
  int lineStart(final int offset) {
    return lineStarts[lineIndex(offset)];
  }

  int line(final int offset) {
    return lineIndex(offset) + 1;
  }

  int column(final int offset) {
    return offset - lineStart(offset) + 1;
  }

  Span span(final int from, final int to) {
    return new Span(line(from), column(from), line(to), column(to), from, to);
  }

  private int lineIndex(final int offset) {
    final int found = Arrays.binarySearch(lineStarts, offset);
    return found >= 0 ? found : -found - 2;
  }

  private static int[] lineStarts(final String text, final int first) {
    final List<Integer> starts = new ArrayList<>();
    starts.add(first);
    int index = first;
    while (index < text.length()) {
      final char c = text.charAt(index);
      final boolean crlf = c == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n';
      index += crlf ? 2 : 1;
      if (c == '\n' || c == '\r') {
        starts.add(index);
      }
    }
    return starts.stream().mapToInt(Integer::intValue).toArray();
  }
}
