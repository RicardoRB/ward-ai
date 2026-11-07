package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code a.equals(b)} when the static types of {@code a} and {@code b} show that no value
 * is both: an array and a type that no array has, or two arrays of different primitive element
 * types or depths. It reports nothing when a type is unknown.
 */
public final class ArrayEqualsCheck implements RuleCheck {

  /** What makes the two types incompatible. */
  public enum Mismatch {
    /** One side is an array and the other a type that no array has. */
    ARRAY_AND_NON_ARRAY,
    /** Both sides are arrays, but their element types or depths differ and one is primitive. */
    INCOMPATIBLE_ARRAYS
  }

  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String EQUALS = "equals";
  private static final Set<String> ARRAY_SUPERTYPES =
      Set.of("java.lang.Object", "java.lang.Cloneable", "java.io.Serializable");
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final Mismatch mismatch;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mismatch what makes the types incompatible
   * @param message the message reported at the call
   */
  public ArrayEqualsCheck(final Mismatch mismatch, final String message) {
    this.mismatch = Objects.requireNonNull(mismatch, "mismatch");
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
      final UnitTypeFacts facts,
      final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(call -> incompatible(call, facts))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private boolean incompatible(final SyntaxNode call, final UnitTypeFacts facts) {
    final List<SyntaxNode> parts = call.children();
    final Optional<SyntaxNode> invoked =
        parts.stream().filter(part -> METHOD_CALL.equals(part.kind())).findFirst();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind()) || invoked.isEmpty()) {
      return false;
    }
    final List<SyntaxNode> arguments = arguments(invoked.get());
    if (arguments.size() != 1 || !isEquals(invoked.get())) {
      return false;
    }
    final Optional<TypeRef> left = facts.typeOf(parts.get(0).span());
    final Optional<TypeRef> right = facts.typeOf(arguments.get(0).span());
    return left.isPresent() && right.isPresent() && differ(left.get(), right.get());
  }

  private boolean differ(final TypeRef left, final TypeRef right) {
    final boolean leftArray = left.arrayDepth() > 0;
    final boolean rightArray = right.arrayDepth() > 0;
    if (mismatch == Mismatch.ARRAY_AND_NON_ARRAY) {
      return leftArray != rightArray && !hasArraySupertype(leftArray ? right : left);
    }
    return leftArray
        && rightArray
        && (left.primitive() || right.primitive())
        && (left.arrayDepth() != right.arrayDepth() || !left.name().equals(right.name()));
  }

  private static boolean hasArraySupertype(final TypeRef nonArray) {
    return ARRAY_SUPERTYPES.contains(nonArray.name());
  }

  private static boolean isEquals(final SyntaxNode invoked) {
    return invoked.children().stream()
        .filter(child -> "identifier".equals(child.kind()))
        .anyMatch(child -> EQUALS.equals(SyntaxWalk.text(child)));
  }

  private static List<SyntaxNode> arguments(final SyntaxNode invoked) {
    return SyntaxWalk.descendants(invoked).stream()
        .filter(node -> "expressionList".equals(node.kind()))
        .findFirst()
        .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
        .orElse(List.of());
  }
}
