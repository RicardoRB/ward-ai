package dev.ward.analysis.domain.rule.check;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** The Shannon entropy of a secret, as Gitleaks 8.30.1 computes it. */
final class Entropy {

  private static final double LOG2 = Math.log(2);

  private Entropy() {}

  /**
   * Returns {@code -Σ f·log2 f} over the code points of {@code text}, with each frequency taken
   * against the UTF-8 byte length, in bits per character.
   */
  static double of(final String text) {
    if (text.isEmpty()) {
      return 0;
    }
    final Map<Integer, Integer> counts = new HashMap<>();
    text.codePoints().forEach(point -> counts.merge(point, 1, Integer::sum));
    final double inverse = 1.0 / text.getBytes(StandardCharsets.UTF_8).length;
    double entropy = 0;
    for (final int count : counts.values()) {
      final double frequency = count * inverse;
      entropy -= frequency * Math.log(frequency) / LOG2;
    }
    return entropy;
  }
}
