package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a lookup in a collection or a map, such as {@code contains}, {@code indexOf} or {@code
 * containsKey}, with an argument that cannot be an element, as PMD's CollectionTypeMismatch does.
 * The method must take an {@code Object}, which is why javac accepts the call. The argument cannot
 * be an element when it and the element type are two different types from a fixed list of final JDK
 * types: {@code String} and the boxed primitives. PMD judges any pair of types; this check judges
 * only these, whose relation the facts decide.
 */
public final class CollectionTypeMismatchCheck extends FactsCheck {

  private static final String COLLECTION = "java.util.Collection";
  private static final String MAP = "java.util.Map";
  private static final String OBJECT = "java.lang.Object";
  private static final String EXPRESSION = "expression";
  private static final Set<String> ELEMENT_LOOKUPS =
      Set.of(
          "contains",
          "remove",
          "removeFirstOccurrence",
          "removeLastOccurrence",
          "indexOf",
          "lastIndexOf");
  private static final Set<String> KEY_LOOKUPS =
      Set.of("containsKey", "get", "getOrDefault", "remove");
  private static final Set<String> FINAL_TYPES =
      Set.of(
          "java.lang.String",
          "java.lang.Boolean",
          "java.lang.Byte",
          "java.lang.Character",
          "java.lang.Short",
          "java.lang.Integer",
          "java.lang.Long",
          "java.lang.Float",
          "java.lang.Double");
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call; {@code {value}} is the two types
   */
  public CollectionTypeMismatchCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(call -> reportOf(call, types).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    if (target.isEmpty()
        || arguments.size() != 1
        || call.children().isEmpty()
        || !isObjectLookup(target.get())) {
      return List.of();
    }
    final SyntaxNode receiver = call.children().get(0);
    final Optional<TypeRef> element = elementType(receiver, target.get().name(), types);
    final Optional<TypeRef> given = types.typeOf(arguments.get(0).span());
    if (element.isEmpty() || given.isEmpty() || !mismatch(element.get(), given.get())) {
      return List.of();
    }
    final String pair = given.get().name() + " in " + element.get().name();
    return List.of(CheckSupport.at(call.span(), CheckSupport.fill(message, pair, "")));
  }

  private static boolean isObjectLookup(final CallTarget target) {
    return target.parameters().size() == 1 && OBJECT.equals(target.parameters().get(0).name());
  }

  private static boolean mismatch(final TypeRef element, final TypeRef given) {
    return FINAL_TYPES.contains(element.name())
        && FINAL_TYPES.contains(given.name())
        && !element.name().equals(given.name())
        && element.arrayDepth() == 0
        && given.arrayDepth() == 0;
  }

  /** Returns the type the lookup compares with: the element of a collection, the key of a map. */
  private static Optional<TypeRef> elementType(
      final SyntaxNode receiver, final String method, final UnitTypeFacts types) {
    final Optional<TypeRef> held = types.typeOf(receiver.span());
    final Set<String> ancestors =
        types.membersOf(receiver.span()).map(TypeMembers::ancestors).stream()
            .flatMap(List::stream)
            .map(TypeRef::name)
            .collect(Collectors.toSet());
    final boolean map =
        ancestors.contains(MAP) || held.filter(t -> MAP.equals(t.name())).isPresent();
    final boolean collection =
        ancestors.contains(COLLECTION) || held.filter(t -> COLLECTION.equals(t.name())).isPresent();
    final boolean applies =
        map && KEY_LOOKUPS.contains(method) || collection && ELEMENT_LOOKUPS.contains(method);
    return held.filter(type -> applies && !type.arguments().isEmpty())
        .map(type -> type.arguments().get(0));
  }
}
