package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.ExpressionTypes;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The type facts of a source unit, fetched in one batch per unit from an optional resolver. A fact
 * the resolver cannot resolve is left out, and a rule treats it as unknown.
 */
@FunctionalInterface
public interface TypeFacts extends TypeResolver {

  /**
   * Returns the facts of {@code kinds} for {@code spans} of {@code unit}.
   *
   * @param unit the unit whose text the spans refer to
   * @param spans the spans of the syntax nodes the rules need facts for
   * @param kinds the kinds of fact to compute
   * @return the answered facts; an unavailable source answers none
   */
  UnitTypeFacts factsOf(SourceUnit unit, List<Span> spans, Set<FactKind> kinds);

  /** The type-name view: the types of {@code expressions}, as {@link TypeRef#display()}. */
  @Override
  default ExpressionTypes typesOf(final SourceUnit unit, final List<Span> expressions) {
    final UnitTypeFacts facts = factsOf(unit, expressions, EnumSet.of(FactKind.TYPE));
    final Map<Span, String> types = new HashMap<>();
    for (final Span span : expressions) {
      final SpanFacts known = facts.at(span);
      if (known.type() != null) {
        types.put(span, known.type().display());
      }
    }
    return new ExpressionTypes(types);
  }
}
