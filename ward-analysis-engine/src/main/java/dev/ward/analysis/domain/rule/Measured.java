package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.Span;

/**
 * One measured declaration: where it is and the value a rule computes for it, whether or not the
 * value breaks the rule's limit.
 *
 * @param span the first and last line of the declaration
 * @param value the measured value
 */
public record Measured(Span span, int value) {}
