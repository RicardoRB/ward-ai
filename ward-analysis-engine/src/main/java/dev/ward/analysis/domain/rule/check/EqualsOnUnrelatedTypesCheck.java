package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
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

/**
 * Reports {@code a.equals(b)} when the type of {@code a} and the type of {@code b} are two
 * different final types, so no value can be both and the call is always false. It is the first
 * check to read type facts, and it reports nothing when a type or a shape is unknown.
 */
public final class EqualsOnUnrelatedTypesCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String EQUALS = "equals";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE, FactKind.SHAPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the two type names
   */
  public EqualsOnUnrelatedTypesCheck(final String message) {
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
        .map(visit -> match(visit.node(), types))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<RuleMatch> match(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = call.children();
    final Optional<SyntaxNode> method =
        parts.stream().filter(part -> METHOD_CALL.equals(part.kind())).findFirst();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind()) || method.isEmpty()) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> argument = onlyArgument(method.get());
    if (argument.isEmpty()) {
      return Optional.empty();
    }
    final Optional<TypeRef> receiver = finalType(parts.get(0).span(), types);
    final Optional<TypeRef> other = finalType(argument.get().span(), types);
    if (receiver.isEmpty() || other.isEmpty() || receiver.get().equals(other.get())) {
      return Optional.empty();
    }
    final String names = receiver.get().display() + " and " + other.get().display();
    return Optional.of(CheckSupport.at(call.span(), CheckSupport.fill(message, names, "")));
  }

  /** The one argument of a call of {@code equals}, if {@code method} is one. */
  private static Optional<SyntaxNode> onlyArgument(final SyntaxNode method) {
    if (!isEquals(method)) {
      return Optional.empty();
    }
    final List<SyntaxNode> expressions =
        SyntaxWalk.descendants(method).stream()
            .filter(node -> "expressionList".equals(node.kind()))
            .findFirst()
            .map(SyntaxNode::children)
            .orElse(List.of())
            .stream()
            .filter(node -> EXPRESSION.equals(node.kind()))
            .toList();
    return expressions.size() == 1 ? Optional.of(expressions.get(0)) : Optional.empty();
  }

  private static boolean isEquals(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> "identifier".equals(child.kind()))
        .anyMatch(child -> EQUALS.equals(SyntaxWalk.text(child)));
  }

  /** The type at {@code span} if it is known, not a primitive, and final. */
  private static Optional<TypeRef> finalType(final Span span, final UnitTypeFacts types) {
    final Optional<TypeRef> type = types.typeOf(span).filter(known -> !known.primitive());
    final Optional<TypeShape> shape = types.shapeOf(span);
    return type.filter(known -> shape.filter(EqualsOnUnrelatedTypesCheck::isFinal).isPresent());
  }

  private static boolean isFinal(final TypeShape shape) {
    return shape.isFinal()
        || shape.kind() == TypeShape.Kind.ENUM
        || shape.kind() == TypeShape.Kind.RECORD;
  }
}
