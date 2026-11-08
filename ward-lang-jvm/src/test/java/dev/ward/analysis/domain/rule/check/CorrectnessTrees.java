package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the small syntax trees and type facts of the correctness check tests. */
final class CorrectnessTrees {

  private CorrectnessTrees() {}

  /** A one-character span at {@code column} of line 1. */
  static Span span(final int column) {
    return new Span(1, column, 1, column, column - 1, column - 1);
  }

  static SyntaxNode leaf(final String kind, final String text, final int column) {
    return new SyntaxNode(kind, span(column), Map.of("text", text), List.of());
  }

  static SyntaxNode node(final String kind, final int column, final SyntaxNode... children) {
    return new SyntaxNode(kind, span(column), Map.of(), List.of(children));
  }

  /** An expression made of one token with the given text. */
  static SyntaxNode expression(final String text, final int column) {
    return node("expression", column, leaf("IDENTIFIER", text, column));
  }

  static TypeRef type(final String name, final boolean primitive) {
    return new TypeRef(name, List.of(), 0, primitive);
  }

  static SpanFacts facts(final TypeRef type) {
    return new SpanFacts(
        type,
        new TypeShape(TypeShape.Kind.CLASS, false, false, false, List.of()),
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  static SpanFacts call(final String declaring, final String returned) {
    return new SpanFacts(
        null,
        null,
        new dev.ward.analysis.domain.types.CallTarget(
            type(declaring, false),
            "trim",
            List.of(),
            type(returned, false),
            false,
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            Map.of(),
            Map.of(),
            null),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  /** Facts keyed by the one-character spans at the given columns. */
  static UnitTypeFacts table(final Object... pairs) {
    final Map<String, SpanFacts> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put(UnitTypeFacts.key(span((Integer) pairs[i])), (SpanFacts) pairs[i + 1]);
    }
    return new UnitTypeFacts(map);
  }
}
