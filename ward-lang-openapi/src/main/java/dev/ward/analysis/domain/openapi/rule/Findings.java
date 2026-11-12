package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.openapi.DocNode;
import java.util.List;

/** Builds the findings of the rules: a message at the key, or the value, of a node. */
final class Findings {

  private Findings() {}

  /** A finding at {@code node}, where Spectral reports it: its value, which a key never is. */
  static OpenApiFinding at(final DocNode node, final String message) {
    return new OpenApiFinding(node.file(), node.reportSpan(), message);
  }

  /** A finding at the value of {@code node}, which is where a value rule points. */
  static OpenApiFinding atValue(final DocNode node, final String message) {
    return new OpenApiFinding(node.file(), node.reportSpan(), message);
  }

  /** A finding at {@code span} of the file of {@code node}. */
  static OpenApiFinding in(final DocNode node, final Span span, final String message) {
    return new OpenApiFinding(node.file(), span, message);
  }

  /** The text of the string at {@code key} of {@code node}; empty when it is missing or empty. */
  static String text(final DocNode node, final String key) {
    return node.string(key).orElse("");
  }

  /** The findings without the repeats Spectral drops: the same file, position and message. */
  static List<OpenApiFinding> distinct(final List<OpenApiFinding> findings) {
    return findings.stream().distinct().toList();
  }
}
