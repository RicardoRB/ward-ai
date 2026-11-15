package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;

/**
 * A synthetic variable that carries the value of a part of a statement to the statement that uses
 * it: the last expression of a lambda, a branch of an {@code if}, or a receiver. The variable is
 * written where the value is made and read where it is used, so the engine's reaching definitions
 * join the branches.
 *
 * @param name the name, which no Kotlin identifier can have
 * @param declaration where the value comes from, which tells two variables of one name apart
 */
record KotlinResult(String name, Span declaration) {

  /** A variable for the value of the part that starts at {@code part}. */
  static KotlinResult of(final String kind, final Span part) {
    return new KotlinResult("$" + kind + "@" + part.startOffset(), part);
  }

  /** The write that makes the value, placed at the start of the node that makes it. */
  VariableAccess write(final Span node) {
    return new VariableAccess(
        name,
        AccessKind.WRITE,
        new Span(
            node.startLine(),
            node.startColumn(),
            node.startLine(),
            node.startColumn(),
            node.startOffset(),
            node.startOffset()),
        declaration);
  }

  /** The read that uses the value, at the place of the part that made it. */
  VariableAccess read(final Span use) {
    return new VariableAccess(name, AccessKind.READ, use, declaration);
  }

  /** The span of a node, widened by one character to the left so a write can start before it. */
  static Span widened(final Span node) {
    final int column = Math.max(1, node.startColumn() - 1);
    return new Span(
        node.startLine(),
        column,
        node.endLine(),
        node.endColumn(),
        Math.max(0, node.startOffset() - 1),
        node.endOffset());
  }
}
