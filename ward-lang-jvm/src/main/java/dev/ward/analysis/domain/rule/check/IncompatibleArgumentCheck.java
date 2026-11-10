package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a call whose argument can never be of the type the call expects: the type argument of the
 * receiver's type (as in {@code List<String>.contains}) or the element type of an array argument
 * (as in {@code Arrays.fill}). The argument is incompatible when it is a primitive or an instance
 * of a final class whose supertypes do not include the expected type. A call whose facts are
 * unknown is never reported.
 */
public final class IncompatibleArgumentCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String OBJECT = "java.lang.Object";
  private static final String NUMBER = "java.lang.Number";
  private static final Set<String> BOX_SUPERTYPES =
      Set.of(OBJECT, "java.lang.Comparable", "java.io.Serializable");
  private static final Set<String> NUMERIC_BOXES =
      Set.of(
          "java.lang.Byte",
          "java.lang.Short",
          "java.lang.Integer",
          "java.lang.Long",
          "java.lang.Float",
          "java.lang.Double");
  private static final Map<String, String> BOXES =
      Map.of(
          "byte", "java.lang.Byte",
          "short", "java.lang.Short",
          "int", "java.lang.Integer",
          "long", "java.lang.Long",
          "float", "java.lang.Float",
          "double", "java.lang.Double",
          "char", "java.lang.Character",
          "boolean", "java.lang.Boolean");
  private static final FactNeed NEED =
      new FactNeed(
          Set.of(EXPRESSION),
          Set.of(FactKind.CALL, FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));

  private final Set<String> declaringTypes;
  private final List<Entry> entries;
  private final String message;

  /**
   * A checked method.
   *
   * @param name the method name
   * @param arity how many arguments the call has
   * @param fromReceiver whether the expected type is a type argument of the receiver's type, and
   *     not the element type of the array argument at {@code source}
   * @param source the index of the receiver's type argument, or of the array argument
   * @param value the index of the argument that must have the expected type
   */
  public record Entry(String name, int arity, boolean fromReceiver, int source, int value) {

    /** Checks the name. */
    public Entry {
      Objects.requireNonNull(name, "name");
    }
  }

  /**
   * Creates the check.
   *
   * @param declaringTypes qualified names of the types that declare the checked methods
   * @param entries the checked methods
   * @param message the message reported at the call
   */
  public IncompatibleArgumentCheck(
      final Set<String> declaringTypes, final List<Entry> entries, final String message) {
    this.declaringTypes = Set.copyOf(declaringTypes);
    this.entries = List.copyOf(entries);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final SyntaxNode call = visit.node();
      if (incompatible(call, types)) {
        matches.add(CheckSupport.at(call.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean incompatible(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    if (target.isEmpty() || !declaringTypes.contains(target.get().declaringType().name())) {
      return false;
    }
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    return entries.stream()
        .filter(entry -> entry.name().equals(target.get().name()))
        .filter(entry -> entry.arity() == arguments.size())
        .anyMatch(entry -> entryIncompatible(entry, call, arguments, types));
  }

  private static boolean entryIncompatible(
      final Entry entry,
      final SyntaxNode call,
      final List<SyntaxNode> arguments,
      final UnitTypeFacts types) {
    final Optional<TypeRef> expected = expected(entry, call, arguments, types);
    final SyntaxNode value = arguments.get(entry.value());
    return expected.isPresent()
        && !OBJECT.equals(expected.get().name())
        && excludes(expected.get(), value, types);
  }

  private static Optional<TypeRef> expected(
      final Entry entry,
      final SyntaxNode call,
      final List<SyntaxNode> arguments,
      final UnitTypeFacts types) {
    if (entry.fromReceiver()) {
      final List<SyntaxNode> parts = call.children();
      return parts.isEmpty()
          ? Optional.empty()
          : types
              .typeOf(parts.get(0).span())
              .filter(receiver -> receiver.arguments().size() > entry.source())
              .map(receiver -> receiver.arguments().get(entry.source()))
              .filter(argument -> argument.arrayDepth() == 0);
    }
    return types
        .typeOf(arguments.get(entry.source()).span())
        .filter(array -> array.arrayDepth() == 1 && !array.primitive())
        .map(array -> new TypeRef(array.name(), array.arguments(), 0, false));
  }

  /** Tells whether no value of the argument's type can be an instance of the expected type. */
  private static boolean excludes(
      final TypeRef expected, final SyntaxNode value, final UnitTypeFacts types) {
    final Optional<TypeRef> type = types.typeOf(value.span());
    if (type.isEmpty() || type.get().arrayDepth() > 0) {
      return false;
    }
    if (type.get().primitive()) {
      return !boxCompatible(BOXES.get(type.get().name()), expected.name());
    }
    final boolean finalClass = types.shapeOf(value.span()).filter(TypeShape::isFinal).isPresent();
    return finalClass
        && types.membersOf(value.span()).isPresent()
        && !expected.name().equals(type.get().name())
        && types.membersOf(value.span()).get().ancestors().stream()
            .noneMatch(ancestor -> ancestor.name().equals(expected.name()));
  }

  private static boolean boxCompatible(final String box, final String expected) {
    return box == null
        || box.equals(expected)
        || BOX_SUPERTYPES.contains(expected)
        || NUMBER.equals(expected) && NUMERIC_BOXES.contains(box);
  }
}
