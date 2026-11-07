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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a comparison of two values whose declared types cannot share a value although javac
 * accepts it: an interface type against a final class that does not implement it, or two interface
 * types where neither extends the other. The comparison is a call of {@code equals} or an {@code
 * ==} or {@code !=}. It reports nothing when a type, shape or its ancestors are unknown.
 */
public final class RelatedTypesCheck implements RuleCheck {

  /** How the two values are compared. */
  public enum Comparison {
    /** {@code a.equals(b)}. */
    EQUALS,
    /** {@code a == b} or {@code a != b}. */
    POINTER
  }

  /** Which unrelated pair of types is reported. */
  public enum Pair {
    /** A final class and an interface it does not implement. */
    CLASS_AND_INTERFACE,
    /** Two interfaces, neither extending the other. */
    INTERFACES
  }

  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final int BINARY = 3;
  private static final Set<String> POINTER_OPERATORS = Set.of("==", "!=");
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));

  private final Comparison comparison;
  private final Pair pair;
  private final String message;

  /**
   * Creates the check.
   *
   * @param comparison how the two values are compared
   * @param pair which unrelated pair of types is reported
   * @param message the message reported at the comparison
   */
  public RelatedTypesCheck(final Comparison comparison, final Pair pair, final String message) {
    this.comparison = Objects.requireNonNull(comparison, "comparison");
    this.pair = Objects.requireNonNull(pair, "pair");
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
        .filter(node -> unrelated(node, types))
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  private boolean unrelated(final SyntaxNode node, final UnitTypeFacts types) {
    final Optional<List<SyntaxNode>> operands =
        comparison == Comparison.EQUALS ? equalsOperands(node) : pointerOperands(node);
    return operands.isPresent() && unrelated(operands.get().get(0), operands.get().get(1), types);
  }

  private static Optional<List<SyntaxNode>> equalsOperands(final SyntaxNode call) {
    final List<SyntaxNode> parts = call.children();
    final Optional<SyntaxNode> invoked =
        parts.stream().filter(part -> METHOD_CALL.equals(part.kind())).findFirst();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind()) || invoked.isEmpty()) {
      return Optional.empty();
    }
    final boolean named =
        invoked.get().children().stream()
            .anyMatch(c -> "identifier".equals(c.kind()) && "equals".equals(SyntaxWalk.text(c)));
    final List<SyntaxNode> arguments =
        SyntaxWalk.descendants(invoked.get()).stream()
            .filter(node -> "expressionList".equals(node.kind()))
            .findFirst()
            .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
            .orElse(List.of());
    return named && arguments.size() == 1
        ? Optional.of(List.of(parts.get(0), arguments.get(0)))
        : Optional.empty();
  }

  private static Optional<List<SyntaxNode>> pointerOperands(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final boolean binary =
        parts.size() == BINARY
            && EXPRESSION.equals(parts.get(0).kind())
            && EXPRESSION.equals(parts.get(2).kind())
            && POINTER_OPERATORS.contains(SyntaxWalk.text(parts.get(1)));
    return binary ? Optional.of(List.of(parts.get(0), parts.get(2))) : Optional.empty();
  }

  /** The declared type of an operand with its shape and, when known, the names above it. */
  private record Operand(TypeRef type, TypeShape shape, Optional<Set<String>> above) {

    boolean isInterface() {
      return shape.kind() == TypeShape.Kind.INTERFACE;
    }

    boolean isFinalClass() {
      return shape.kind() != TypeShape.Kind.INTERFACE
          && (shape.isFinal()
              || shape.kind() == TypeShape.Kind.ENUM
              || shape.kind() == TypeShape.Kind.RECORD);
    }

    boolean extendsOrImplements(final Operand other) {
      return above.map(names -> names.contains(other.type().name())).orElse(true);
    }
  }

  private boolean unrelated(
      final SyntaxNode left, final SyntaxNode right, final UnitTypeFacts types) {
    final Optional<Operand> first = operand(left, types);
    final Optional<Operand> second = operand(right, types);
    if (first.isEmpty()
        || second.isEmpty()
        || first.get().type().name().equals(second.get().type().name())) {
      return false;
    }
    return pair == Pair.INTERFACES
        ? interfaces(first.get(), second.get())
        : classAndInterface(first.get(), second.get())
            || classAndInterface(second.get(), first.get());
  }

  private static Optional<Operand> operand(final SyntaxNode node, final UnitTypeFacts types) {
    final Optional<TypeRef> type =
        types.typeOf(node.span()).filter(known -> !known.primitive() && known.arrayDepth() == 0);
    final Optional<TypeShape> shape = types.shapeOf(node.span());
    if (type.isEmpty() || shape.isEmpty()) {
      return Optional.empty();
    }
    final Optional<Set<String>> above =
        types
            .membersOf(node.span())
            .map(
                members ->
                    members.ancestors().stream().map(TypeRef::name).collect(Collectors.toSet()));
    return Optional.of(new Operand(type.get(), shape.get(), above));
  }

  private static boolean interfaces(final Operand left, final Operand right) {
    return left.isInterface()
        && right.isInterface()
        && left.above().isPresent()
        && right.above().isPresent()
        && !left.extendsOrImplements(right)
        && !right.extendsOrImplements(left);
  }

  private static boolean classAndInterface(final Operand finalClass, final Operand other) {
    return finalClass.isFinalClass()
        && other.isInterface()
        && finalClass.above().isPresent()
        && !finalClass.extendsOrImplements(other);
  }
}
