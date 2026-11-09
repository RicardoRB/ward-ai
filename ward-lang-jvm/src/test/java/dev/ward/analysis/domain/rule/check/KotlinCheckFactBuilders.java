package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the type facts of the cast, catch and platform type checks' tests, by hand. */
final class KotlinCheckFactBuilders {
  private static final String STRING = "java.lang.String";
  private static final String GET_PROPERTY = "getProperty";

  private KotlinCheckFactBuilders() {}

  /** Gives the operand and the target of {@code cast} their facts. */
  static UnitTypeFacts casted(
      final SyntaxNode cast, final SpanFacts source, final SpanFacts target) {
    final Map<String, SpanFacts> facts = new HashMap<>();
    facts.put(UnitTypeFacts.startKey(cast.children().get(0).span()), source);
    facts.put(UnitTypeFacts.startKey(cast.children().get(2).span()), target);
    return new UnitTypeFacts(facts);
  }

  /** Gives the exception types of the first and second catch clauses their facts. */
  static UnitTypeFacts catches(
      final SyntaxNode root,
      final String firstType,
      final List<String> firstAncestors,
      final String secondType,
      final List<String> secondAncestors) {
    final Map<String, SpanFacts> facts = new HashMap<>();
    final List<SyntaxNode> types = KotlinTypedCheckFixtures.nodes(root, "userType");
    facts.put(UnitTypeFacts.startKey(types.get(0).span()), named(firstType, firstAncestors));
    facts.put(UnitTypeFacts.startKey(types.get(1).span()), named(secondType, secondAncestors));
    return new UnitTypeFacts(facts);
  }

  /** Gives every node of an expression kind the call target {@code target}. */
  static UnitTypeFacts calledAt(final SyntaxNode root, final CallTarget target) {
    final Map<String, SpanFacts> facts = new HashMap<>();
    for (final SyntaxNode node : KotlinTypedCheckFixtures.nodes(root, "expression")) {
      facts.put(UnitTypeFacts.startKey(node.span()), call(target));
    }
    return new UnitTypeFacts(facts);
  }

  static SpanFacts typed(
      final String name, final String kind, final boolean isFinal, final List<String> ancestors) {
    final TypeShape shape =
        new TypeShape(
            TypeShape.Kind.valueOf(kind.toUpperCase(Locale.ROOT)),
            false,
            isFinal,
            false,
            ancestors.stream().limit(1).map(TypeRef::of).toList());
    return new SpanFacts(
        TypeRef.of(name),
        shape,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        members(ancestors));
  }

  static SpanFacts named(final String name, final List<String> ancestors) {
    return new SpanFacts(
        TypeRef.of(name),
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        ancestors == null ? null : members(ancestors));
  }

  static SpanFacts call(final CallTarget target) {
    return new SpanFacts(
        null, null, target, List.of(), List.of(), List.of(), List.of(), null, null);
  }

  static TypeMembers members(final List<String> ancestors) {
    return new TypeMembers(
        List.of(),
        List.of(),
        ancestors.stream().map(TypeRef::of).toList(),
        List.of(),
        List.of(),
        List.of(),
        Map.of(),
        List.of());
  }

  static CallTarget javaCall(final String declaring, final List<String> annotations) {
    return new CallTarget(
        TypeRef.of(declaring),
        GET_PROPERTY,
        List.of(TypeRef.of(STRING)),
        TypeRef.of(STRING),
        true,
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
}
