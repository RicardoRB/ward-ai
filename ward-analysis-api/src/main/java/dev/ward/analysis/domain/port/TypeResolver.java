package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.ExpressionTypes;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;

/** Answers the static type of expression spans in one source unit, batched per file. */
@FunctionalInterface
public interface TypeResolver {

  /**
   * Returns the types of {@code expressions}, the spans of {@code unit}. A span the resolver cannot
   * answer is left out of the result; a resolver that is unavailable answers none.
   *
   * @param unit the unit whose text the spans refer to
   * @param expressions the spans to type
   * @return the answered spans
   */
  ExpressionTypes typesOf(SourceUnit unit, List<Span> expressions);
}
