package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a lookup {@code map[key]!!} or {@code map.get(key)!!} on a receiver of type {@code
 * java.util.Map}, or of a type that extends it. A receiver whose type is unknown is never reported.
 */
public final class KotlinMapGetNotNullCheck implements RuleCheck {
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String ATOMIC = "atomicExpression";
  private static final String OPERATION = "postfixUnaryOperation";
  private static final String NOT_NULL = "!!";
  private static final String MAP = "java.util.Map";
  private static final String MEMBER_ACCESS = "memberAccessOperator";
  private static final String GET = "get";
  private static final String INDEX_OPEN = "[";
  private static final String INDEX_CLOSE = "]";
  private static final String CALL_OPEN = "(";
  private static final int PARTS = 3;
  private static final int GET_PARTS = 2;
  private static final int RECEIVER = 0;
  private static final int ACCESS = 1;
  private static final int ASSERTION = 2;
  private static final FactNeed NEED =
      new FactNeed(Set.of(ATOMIC), Set.of(FactKind.TYPE, FactKind.SHAPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the lookup
   */
  public KotlinMapGetNotNullCheck(final String message) {
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
    return SyntaxWalk.visits(root, Set.of(POSTFIX)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(lookup -> isMapLookup(lookup, types))
        .map(lookup -> CheckSupport.at(lookup.span(), message))
        .toList();
  }

  /**
   * Matches two shapes of the Kotlin tree: {@code m[k]!!} is flat, with the index and the {@code !!}
   * as siblings, while {@code m.get(k)!!} nests the {@code !!} inside the {@code .get(...)} call.
   */
  private static boolean isMapLookup(final SyntaxNode lookup, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = lookup.children();
    return !parts.isEmpty()
        && ATOMIC.equals(parts.get(RECEIVER).kind())
        && isMap(parts.get(RECEIVER).span(), types)
        && (isFlatIndex(parts) || isNestedGet(parts));
  }

  private static boolean isFlatIndex(final List<SyntaxNode> parts) {
    return parts.size() == PARTS
        && isIndex(parts.get(ACCESS))
        && isAssertion(parts.get(ASSERTION));
  }

  private static boolean isNestedGet(final List<SyntaxNode> parts) {
    if (parts.size() != GET_PARTS || !isMemberAccess(parts.get(ACCESS))) {
      return false;
    }
    final List<SyntaxNode> call = parts.get(ACCESS).children().get(1).children();
    return call.size() == PARTS
        && ATOMIC.equals(call.get(RECEIVER).kind())
        && GET.equals(SyntaxWalk.text(call.get(RECEIVER)))
        && OPERATION.equals(call.get(ACCESS).kind())
        && SyntaxWalk.text(call.get(ACCESS)).startsWith(CALL_OPEN)
        && isAssertion(call.get(ASSERTION));
  }

  private static boolean isMemberAccess(final SyntaxNode access) {
    return OPERATION.equals(access.kind())
        && access.children().size() == GET_PARTS
        && MEMBER_ACCESS.equals(access.children().get(RECEIVER).kind())
        && POSTFIX.equals(access.children().get(ACCESS).kind());
  }

  private static boolean isIndex(final SyntaxNode access) {
    if (!OPERATION.equals(access.kind())) {
      return false;
    }
    final String text = SyntaxWalk.text(access);
    return text.startsWith(INDEX_OPEN) && text.endsWith(INDEX_CLOSE);
  }

  private static boolean isAssertion(final SyntaxNode node) {
    return OPERATION.equals(node.kind()) && NOT_NULL.equals(SyntaxWalk.text(node));
  }

  private static boolean isMap(final Span receiver, final UnitTypeFacts types) {
    final boolean named =
        types
            .typeOf(receiver)
            .filter(type -> type.arrayDepth() == 0 && MAP.equals(type.name()))
            .isPresent();
    return named
        || types
            .shapeOf(receiver)
            .filter(shape -> shape.supertypes().stream().anyMatch(s -> MAP.equals(s.name())))
            .isPresent();
  }
}
