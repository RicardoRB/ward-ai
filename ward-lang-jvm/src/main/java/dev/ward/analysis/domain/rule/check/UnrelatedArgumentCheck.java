package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a call of {@code contains}, {@code indexOf}, {@code remove}, {@code get} or {@code
 * containsKey} on a {@code java.util} collection or map whose argument can never equal an element:
 * the argument has a final type that is neither the element type nor a subtype of it. It reports
 * nothing when a type, a shape or the ancestors of the argument type are unknown.
 */
public final class UnrelatedArgumentCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String JAVA_UTIL = "java.util.";
  private static final Set<String> BY_ELEMENT =
      Set.of("contains", "remove", "indexOf", "lastIndexOf");
  private static final Set<String> BY_KEY = Set.of("get", "containsKey", "remove");
  private static final Set<String> BOX_ANCESTORS =
      Set.of(
          "java.lang.Number",
          "java.lang.Object",
          "java.lang.Comparable",
          "java.io.Serializable",
          "java.lang.constant.Constable",
          "java.lang.constant.ConstantDesc");
  private static final Map<String, String> BOXES =
      Map.of(
          "int", "java.lang.Integer",
          "long", "java.lang.Long",
          "short", "java.lang.Short",
          "byte", "java.lang.Byte",
          "double", "java.lang.Double",
          "float", "java.lang.Float");
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public UnrelatedArgumentCheck(final String message) {
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
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(call -> unrelated(call, types))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private static boolean unrelated(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = call.children();
    final Optional<SyntaxNode> invoked =
        parts.stream().filter(part -> METHOD_CALL.equals(part.kind())).findFirst();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind()) || invoked.isEmpty()) {
      return false;
    }
    final Optional<SyntaxNode> argument = onlyArgument(invoked.get());
    final Optional<TypeRef> receiver = types.typeOf(parts.get(0).span());
    final String method = methodName(invoked.get());
    if (argument.isEmpty() || receiver.isEmpty() || !receiver.get().name().startsWith(JAVA_UTIL)) {
      return false;
    }
    final boolean map = receiver.get().arguments().size() == 2;
    final boolean collection = receiver.get().arguments().size() == 1;
    final boolean asked = map ? BY_KEY.contains(method) : collection && BY_ELEMENT.contains(method);
    return asked && excludes(receiver.get().arguments().get(0), argument.get(), types);
  }

  /** Returns whether the argument can never be equal to a value of the element type. */
  static boolean excludes(
      final TypeRef element, final SyntaxNode argument, final UnitTypeFacts types) {
    final Optional<TypeRef> type = types.typeOf(argument.span());
    if (type.isEmpty() || type.get().arrayDepth() > 0 || !element.name().contains(".")) {
      return false;
    }
    if (type.get().primitive()) {
      final String box = BOXES.get(type.get().name());
      return box != null && !box.equals(element.name()) && !BOX_ANCESTORS.contains(element.name());
    }
    final boolean isFinal =
        types.shapeOf(argument.span()).filter(UnrelatedArgumentCheck::finalType).isPresent();
    final Optional<Set<String>> above =
        types
            .membersOf(argument.span())
            .map(members -> Set.copyOf(members.ancestors().stream().map(TypeRef::name).toList()));
    return isFinal
        && above.isPresent()
        && !element.name().equals(type.get().name())
        && !above.get().contains(element.name());
  }

  private static boolean finalType(final TypeShape shape) {
    return shape.kind() != TypeShape.Kind.INTERFACE
        && (shape.isFinal()
            || shape.kind() == TypeShape.Kind.ENUM
            || shape.kind() == TypeShape.Kind.RECORD);
  }

  private static String methodName(final SyntaxNode invoked) {
    return invoked.children().stream()
        .filter(child -> "identifier".equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static Optional<SyntaxNode> onlyArgument(final SyntaxNode invoked) {
    final List<SyntaxNode> arguments =
        SyntaxWalk.descendants(invoked).stream()
            .filter(node -> "expressionList".equals(node.kind()))
            .findFirst()
            .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
            .orElse(List.of());
    return arguments.size() == 1 ? Optional.of(arguments.get(0)) : Optional.empty();
  }
}
