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
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a binary expression whose operator is one of a set and whose left operand has one of a
 * set of types. It can also require the right operand to have the same kind of type, or to be an
 * integer literal outside a range. It reports nothing when a type is unknown.
 */
public final class BinaryOperandTypeCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final int OPERANDS = 3;
  private static final int HEX = 16;
  private static final int DECIMAL = 10;
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final Set<String> operators;
  private final Map<String, Range> types;
  private final boolean both;
  private final String message;

  /**
   * The literals that are allowed on the right of the operator.
   *
   * @param min the lowest allowed literal, if the right must be a literal
   * @param max the highest allowed literal, if the right must be a literal
   */
  public record Range(Optional<BigInteger> min, Optional<BigInteger> max) {

    /** The range that does not limit the right operand. */
    public static final Range ANY = new Range(Optional.empty(), Optional.empty());

    /**
     * Checks the bounds.
     *
     * @throws NullPointerException if a bound is missing
     */
    public Range {
      Objects.requireNonNull(min, "min");
      Objects.requireNonNull(max, "max");
    }

    boolean limits() {
      return min.isPresent() || max.isPresent();
    }

    boolean excludes(final BigInteger value) {
      return min.filter(low -> value.compareTo(low) < 0).isPresent()
          || max.filter(high -> value.compareTo(high) > 0).isPresent();
    }
  }

  /**
   * Creates the check.
   *
   * @param operators the operators, such as {@code |} or {@code ==}
   * @param types the names of the types of the left operand, each with the literals that are
   *     allowed on the right for that type, or {@link Range#ANY}
   * @param both whether the right operand must have one of the types as well
   * @param message the message reported at the expression
   */
  public BinaryOperandTypeCheck(
      final Set<String> operators,
      final Map<String, Range> types,
      final boolean both,
      final String message) {
    this.operators = Set.copyOf(operators);
    this.types = Map.copyOf(types);
    this.both = both;
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
        .filter(node -> reported(node, facts))
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  private boolean reported(final SyntaxNode node, final UnitTypeFacts facts) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() < OPERANDS
        || !EXPRESSION.equals(parts.get(0).kind())
        || !EXPRESSION.equals(parts.get(parts.size() - 1).kind())) {
      return false;
    }
    final SyntaxNode left = parts.get(0);
    final SyntaxNode right = parts.get(parts.size() - 1);
    final String operator =
        parts.subList(1, parts.size() - 1).stream()
            .map(SyntaxWalk::text)
            .collect(Collectors.joining());
    final Optional<Range> range = typeOf(left, facts).map(types::get);
    return operators.contains(operator)
        && range.isPresent()
        && (!both || typeOf(right, facts).filter(types::containsKey).isPresent())
        && outsideRange(right, range.get());
  }

  private static Optional<String> typeOf(final SyntaxNode operand, final UnitTypeFacts facts) {
    return facts.typeOf(operand.span()).map(TypeRef::name);
  }

  private static boolean outsideRange(final SyntaxNode right, final Range range) {
    if (!range.limits()) {
      return true;
    }
    final String text = SyntaxWalk.text(right).replace("_", "");
    final boolean suffixed = text.endsWith("L") || text.endsWith("l");
    final String digits = suffixed ? text.substring(0, text.length() - 1) : text;
    return literal(digits).filter(range::excludes).isPresent();
  }

  private static Optional<BigInteger> literal(final String digits) {
    final boolean negative = digits.startsWith("-");
    final String body = negative ? digits.substring(1) : digits;
    final boolean hex = body.startsWith("0x") || body.startsWith("0X");
    final int radix = hex ? HEX : DECIMAL;
    final String number = hex ? body.substring(2) : body;
    if (number.isEmpty() || !number.chars().allMatch(c -> Character.digit(c, radix) >= 0)) {
      return Optional.empty();
    }
    final BigInteger magnitude = new BigInteger(number, radix);
    return Optional.of(negative ? magnitude.negate() : magnitude);
  }
}
