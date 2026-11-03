package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.Span;

/**
 * One node that breaks its limit or naming style.
 *
 * @param span where the node is
 * @param name the declared name, or empty when the node has none
 * @param value the measured value
 * @param expected the style the name should follow, or empty for limits
 */
record Violation(Span span, String name, int value, String expected) {}
