package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the small syntax trees and type facts of the array, instanceof and parameter tests. */
final class MoreTrees {

  private MoreTrees() {}

  static Span span(final int column) {
    return new Span(1, column, 1, column, column - 1, column - 1);
  }

  static SyntaxNode leaf(final String kind, final String text, final int column) {
    return new SyntaxNode(kind, span(column), Map.of("text", text), List.of());
  }

  static SyntaxNode node(final String kind, final int column, final SyntaxNode... children) {
    return new SyntaxNode(kind, span(column), Map.of(), List.of(children));
  }

  static TypeRef type(final String name, final int depth, final boolean primitive) {
    return new TypeRef(name, List.of(), depth, primitive);
  }

  static SpanFacts typed(final TypeRef type) {
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

  static SpanFacts called(final List<TypeRef> parameters, final boolean varargs) {
    return new SpanFacts(
        null,
        null,
        new CallTarget(
            type("java.lang.String", 0, false),
            "format",
            parameters,
            type("java.lang.String", 0, false),
            true,
            varargs,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            Map.of(),
            Map.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  static UnitTypeFacts table(final Object... pairs) {
    final Map<String, SpanFacts> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      final Span key = pairs[i] instanceof Integer column ? span(column) : (Span) pairs[i];
      map.put(UnitTypeFacts.key(key), (SpanFacts) pairs[i + 1]);
    }
    return new UnitTypeFacts(map);
  }
}
