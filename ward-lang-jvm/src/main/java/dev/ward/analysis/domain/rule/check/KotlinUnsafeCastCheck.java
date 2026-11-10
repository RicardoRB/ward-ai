package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a Kotlin {@code value as Target} that can never succeed: both the value's class and the
 * target are known classes, neither is a subtype of the other, and at least one of them is final.
 * Subtyping reads all ancestors, not only the direct supertypes. Anything unknown is no finding.
 */
public final class KotlinUnsafeCastCheck implements RuleCheck {
  private static final String CAST = "typeRHS";
  private static final String OPERATION = "typeOperation";
  private static final String AS = "as";
  private static final String PREFIX = "prefixUnaryExpression";
  private static final int PARTS = 3;
  private static final int OPERAND = 0;
  private static final int OPERATOR = 1;
  private static final int TARGET = 2;
  private static final FactNeed NEED =
      new FactNeed(Set.of(PREFIX), Set.of(FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the cast
   */
  public KotlinUnsafeCastCheck(final String message) {
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
    return SyntaxWalk.visits(root, Set.of(CAST)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(cast -> isUnsafe(cast, types))
        .map(cast -> CheckSupport.at(cast.span(), message))
        .toList();
  }

  private static boolean isUnsafe(final SyntaxNode cast, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = cast.children();
    if (parts.size() != PARTS
        || !OPERATION.equals(parts.get(OPERATOR).kind())
        || !AS.equals(SyntaxWalk.text(parts.get(OPERATOR)))) {
      return false;
    }
    final Span from = parts.get(OPERAND).span();
    final Span to = parts.get(TARGET).span();
    final Optional<Known> source = known(from, types);
    final Optional<Known> target = known(to, types);
    return source.isPresent()
        && target.isPresent()
        && (source.get().shape().isFinal() || target.get().shape().isFinal())
        && !mayBeRelated(source.get().type(), from, target.get().type(), to, types);
  }

  /** A type with its shape, for a value whose class is known. */
  private record Known(TypeRef type, TypeShape shape) {}

  /** Returns the class of the value at {@code span}, when its type and shape are both known. */
  private static Optional<Known> known(final Span span, final UnitTypeFacts types) {
    final Optional<TypeRef> type = types.typeOf(span);
    final Optional<TypeShape> shape = types.shapeOf(span);
    return type.isPresent() && shape.isPresent() && isClass(type.get(), shape.get())
        ? Optional.of(new Known(type.get(), shape.get()))
        : Optional.empty();
  }

  /** Returns whether a type is a known class, not an interface, an annotation or an array. */
  private static boolean isClass(final TypeRef type, final TypeShape shape) {
    return type.arrayDepth() == 0
        && (shape.kind() == TypeShape.Kind.CLASS
            || shape.kind() == TypeShape.Kind.ENUM
            || shape.kind() == TypeShape.Kind.RECORD);
  }

  /**
   * Returns whether one type is, or may be, a subtype of the other. Without the ancestors of both,
   * the answer is that it may be, so the cast is not reported.
   */
  private static boolean mayBeRelated(
      final TypeRef source,
      final Span from,
      final TypeRef target,
      final Span to,
      final UnitTypeFacts types) {
    final Optional<TypeMembers> sourceMembers = types.membersOf(from);
    final Optional<TypeMembers> targetMembers = types.membersOf(to);
    if (sourceMembers.isEmpty() || targetMembers.isEmpty()) {
      return true;
    }
    return source.name().equals(target.name())
        || names(sourceMembers.get()).contains(target.name())
        || names(targetMembers.get()).contains(source.name());
  }

  private static Set<String> names(final TypeMembers members) {
    return members.ancestors().stream().map(TypeRef::name).collect(Collectors.toSet());
  }
}
