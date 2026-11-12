package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.Measured;
import dev.ward.domain.gate.FileLines;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Scores the functions of one file from their complexity and the lines a report covers. */
final class CrapFunctions {

  /** A function with its score. */
  record Scored(Span span, CrapScore score) {}

  private CrapFunctions() {}

  /**
   * Scores each function of {@code measured}. A coverable line counts only for the innermost
   * function whose span contains it; a function with no coverable line, or a file with no lines, is
   * scored from its complexity alone.
   */
  static List<Scored> score(final List<Measured> measured, final Optional<FileLines> lines) {
    final int[] coverable = new int[measured.size()];
    final int[] covered = new int[measured.size()];
    lines.ifPresent(
        file -> {
          for (final int line : file.coverable()) {
            final int owner = innermost(measured, line);
            if (owner >= 0) {
              coverable[owner]++;
              covered[owner] += file.covered().contains(line) ? 1 : 0;
            }
          }
        });
    final List<Scored> scored = new ArrayList<>();
    for (int index = 0; index < measured.size(); index++) {
      final Measured function = measured.get(index);
      scored.add(
          new Scored(
              function.span(),
              CrapScore.of(function.value(), coverage(coverable[index], covered[index]))));
    }
    return scored;
  }

  private static OptionalDouble coverage(final int coverable, final int covered) {
    return coverable == 0
        ? OptionalDouble.empty()
        : OptionalDouble.of((double) covered / coverable);
  }

  /** Returns the index of the smallest function that contains {@code line}, or -1. */
  private static int innermost(final List<Measured> measured, final int line) {
    int best = -1;
    int bestSize = Integer.MAX_VALUE;
    for (int index = 0; index < measured.size(); index++) {
      final Span span = measured.get(index).span();
      final int size = span.endLine() - span.startLine();
      if (span.startLine() <= line && line <= span.endLine() && size < bestSize) {
        best = index;
        bestSize = size;
      }
    }
    return best;
  }
}
