package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code charAt}, {@code codePointAt} and {@code substring} on a string that is a
 * compile-time constant, with constant indexes that lie outside it. The call always throws. It
 * reports nothing when the string or an index is not a known constant.
 */
public final class StringRangeCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final int ONE = 1;
  private static final int TWO = 2;
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CONSTANT));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public StringRangeCheck(final String message) {
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
        .filter(call -> outOfRange(call, types))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private static boolean outOfRange(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = call.children();
    final Optional<SyntaxNode> invoked =
        parts.stream().filter(part -> METHOD_CALL.equals(part.kind())).findFirst();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind()) || invoked.isEmpty()) {
      return false;
    }
    final Optional<Integer> length =
        types
            .constant(parts.get(0).span())
            .map(ConstantValue::value)
            .filter(String.class::isInstance)
            .map(value -> ((String) value).length());
    final String method =
        invoked.get().children().stream()
            .filter(c -> "identifier".equals(c.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    final List<Optional<Long>> indexes =
        arguments(invoked.get()).stream().map(arg -> integer(arg, types)).toList();
    return length.isPresent()
        && indexes.stream().allMatch(Optional::isPresent)
        && violates(method, length.get(), indexes.stream().map(Optional::get).toList());
  }

  private static boolean violates(final String method, final long length, final List<Long> index) {
    return switch (method) {
      case "charAt", "codePointAt" ->
          index.size() == ONE && (index.get(0) < 0 || index.get(0) >= length);
      case "substring" -> substringOutside(length, index);
      default -> false;
    };
  }

  private static boolean substringOutside(final long length, final List<Long> index) {
    if (index.size() == ONE) {
      return index.get(0) < 0 || index.get(0) > length;
    }
    return index.size() == TWO
        && (index.get(0) < 0 || index.get(1) > length || index.get(0) > index.get(1));
  }

  private static Optional<Long> integer(final SyntaxNode argument, final UnitTypeFacts types) {
    return types
        .constant(argument.span())
        .map(ConstantValue::value)
        .filter(Number.class::isInstance)
        .map(value -> ((Number) value).longValue());
  }

  private static List<SyntaxNode> arguments(final SyntaxNode invoked) {
    return SyntaxWalk.descendants(invoked).stream()
        .filter(node -> "expressionList".equals(node.kind()))
        .findFirst()
        .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
        .orElse(List.of());
  }
}
