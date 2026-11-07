package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the syntax trees and type facts of {@link EqualsOnUnrelatedTypesCheckTest}. */
final class Calls {
  static final Span CALL = new Span(1, 1, 1, 20, 0, 19);
  static final Span RECEIVER = new Span(1, 1, 1, 5, 0, 4);
  static final Span ARGUMENT = new Span(1, 13, 1, 18, 12, 17);
  static final Span SECOND = new Span(1, 15, 1, 18, 14, 17);
  static final String EQUALS = "equals";
  static final String TYPE_A = "a.A";
  static final String TYPE_B = "b.B";

  static final SourceUnit UNIT = new SourceUnit("A.java", AnalysisLanguage.JAVA, "text");
  static final EqualsOnUnrelatedTypesCheck CHECK = new EqualsOnUnrelatedTypesCheck("{value}");

  private Calls() {}

  static List<RuleMatch> run(final SyntaxNode root, final UnitTypeFacts types) {
    return CHECK.matches(root, FlowView.NONE, ScopeView.NONE, types, UNIT);
  }

  static SyntaxNode call(final String method, final Span... arguments) {
    final List<SyntaxNode> expressions = new java.util.ArrayList<>();
    for (final Span span : arguments) {
      expressions.add(node("expression", span));
    }
    final SyntaxNode identifier =
        node(
            "identifier",
            RECEIVER,
            new SyntaxNode("IDENTIFIER", RECEIVER, Map.of("text", method), List.of()));
    final SyntaxNode list = new SyntaxNode("expressionList", ARGUMENT, Map.of(), expressions);
    final SyntaxNode args = node("arguments", ARGUMENT, list);
    return node(
        "expression",
        CALL,
        node("expression", RECEIVER),
        new SyntaxNode(".", RECEIVER, Map.of("text", "."), List.of()),
        node("methodCall", CALL, identifier, args));
  }

  static SyntaxNode node(final String kind, final Span span, final SyntaxNode... children) {
    return new SyntaxNode(kind, span, Map.of(), List.of(children));
  }

  static SpanFacts type(final String name, final boolean isFinal) {
    return facts(TypeRef.of(name), TypeShape.Kind.CLASS, isFinal);
  }

  static SpanFacts facts(final TypeRef type, final TypeShape.Kind kind, final boolean isFinal) {
    return new SpanFacts(
        type,
        new TypeShape(kind, false, isFinal, false, List.of()),
        null,
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
      map.put(UnitTypeFacts.key((Span) pairs[i]), (SpanFacts) pairs[i + 1]);
    }
    return new UnitTypeFacts(map);
  }
}
