package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.Span;
import java.util.Map;
import java.util.Optional;

/** Finds the entry recorded for a span, or else for the smallest recorded span around it. */
final class Containing {
  private Containing() {}

  static <T> Optional<T> of(final Map<Long, T> entries, final Span span) {
    final T exact = entries.get(Session.key(span));
    if (exact != null) {
      return Optional.of(exact);
    }
    T best = null;
    long width = Long.MAX_VALUE;
    for (final Map.Entry<Long, T> entry : entries.entrySet()) {
      final int start = (int) (entry.getKey() >> Integer.SIZE);
      final int end = (int) (long) entry.getKey();
      if (start <= span.startOffset() && end >= span.endOffset() && end - start < width) {
        width = end - start;
        best = entry.getValue();
      }
    }
    return Optional.ofNullable(best);
  }
}
