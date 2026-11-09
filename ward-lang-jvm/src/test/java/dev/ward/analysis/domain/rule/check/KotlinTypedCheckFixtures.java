package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds Kotlin trees and the type facts of their nodes, by hand, for the typed checks' tests. */
final class KotlinTypedCheckFixtures {

  private KotlinTypedCheckFixtures() {}

  static SourceUnit unit(final String source) {
    return new SourceUnit("a.kt", AnalysisLanguage.KOTLIN, source);
  }

  static SyntaxNode parse(final String source) {
    return new AntlrLanguageFrontend().parse(unit(source)).tree();
  }

  /** Gives every node of {@code kind} the type {@code type}. */
  static UnitTypeFacts typed(final SyntaxNode root, final String kind, final TypeRef type) {
    final SpanFacts fact = facts(type, null, List.of());
    final Map<String, SpanFacts> facts = new HashMap<>();
    for (final SyntaxNode node : nodes(root, kind)) {
      facts.put(UnitTypeFacts.startKey(node.span()), fact);
    }
    return new UnitTypeFacts(facts);
  }

  /** Gives the postfix call starting at {@code key} the call target {@code target}. */
  static UnitTypeFacts calls(final SyntaxNode root, final String key, final CallTarget target) {
    final Map<String, SpanFacts> facts = new HashMap<>();
    for (final SyntaxNode node : nodes(root, "postfixUnaryExpression")) {
      if (key.equals(UnitTypeFacts.startKey(node.span()))) {
        facts.put(key, facts(null, target, List.of()));
      }
    }
    return new UnitTypeFacts(facts);
  }

  /** Marks every node of {@code kind} with an annotation, as the typer marks a nullness. */
  static UnitTypeFacts marked(final SyntaxNode root, final String kind, final String marker) {
    final SpanFacts fact = facts(null, null, List.of(marker));
    final Map<String, SpanFacts> facts = new HashMap<>();
    for (final SyntaxNode node : nodes(root, kind)) {
      facts.put(UnitTypeFacts.startKey(node.span()), fact);
    }
    return new UnitTypeFacts(facts);
  }

  /** A call target of a method {@code app.Repo#save} with the given annotations. */
  static CallTarget target(final List<String> annotations) {
    return new CallTarget(
        TypeRef.of("app.Repo"),
        "save",
        List.of(),
        null,
        false,
        false,
        annotations,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        Map.of(),
        Map.of(),
        null);
  }

  /** A call target of a method with the given parameter types. */
  static CallTarget callTarget(final String name, final List<TypeRef> parameters) {
    return new CallTarget(
        TypeRef.of("app.A"),
        name,
        parameters,
        null,
        true,
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
        null);
  }

  static List<SyntaxNode> nodes(final SyntaxNode root, final String kind) {
    final List<SyntaxNode> found = new ArrayList<>();
    collect(root, kind, found);
    return found;
  }

  private static void collect(
      final SyntaxNode node, final String kind, final List<SyntaxNode> found) {
    if (kind.equals(node.kind())) {
      found.add(node);
    }
    node.children().forEach(child -> collect(child, kind, found));
  }

  private static SpanFacts facts(
      final TypeRef type, final CallTarget call, final List<String> annotations) {
    return new SpanFacts(
        type, null, call, annotations, List.of(), List.of(), List.of(), null, null);
  }
}
