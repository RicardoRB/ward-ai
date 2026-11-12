package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.openapi.DocNode;

/** Builds the findings of the rules: a message at the key, or the value, of a node. */
final class Findings {

  private Findings() {}

  /** A finding at the key of {@code node}, or at its value when it has no key. */
  static OpenApiFinding at(final DocNode node, final String message) {
    return new OpenApiFinding(node.file(), node.anchor(), message);
  }

  /** A finding at the value of {@code node}, which is where a value rule points. */
  static OpenApiFinding atValue(final DocNode node, final String message) {
    return new OpenApiFinding(node.file(), node.span(), message);
  }

  /** A finding at {@code span} of the file of {@code node}. */
  static OpenApiFinding in(final DocNode node, final Span span, final String message) {
    return new OpenApiFinding(node.file(), span, message);
  }

  /** The text of the string at {@code key} of {@code node}; empty when it is missing or empty. */
  static String text(final DocNode node, final String key) {
    return node.string(key).orElse("");
  }
}
