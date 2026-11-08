package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports integer arithmetic whose result is widened too late: an {@code int} division that is cast
 * to {@code double} or {@code float}, an {@code int} multiplication that is cast to or stored in a
 * {@code long}, and the average {@code (a + b) / 2} of two {@code int}s. It reports nothing when
 * the type of an operand is unknown.
 */
public final class IntegerArithmeticCheck extends FactsCheck {

  /** What the check looks for. */
  public enum Mode {
    /** {@code (double) (a / b)} with integral operands. */
    DIVISION_CAST_TO_FLOATING,
    /** {@code (long) (a * b)} or {@code long x = a * b} with {@code int} operands. */
    MULTIPLICATION_WIDENED,
    /** {@code (a + b) / 2} with {@code int} operands that are not literals. */
    AVERAGE
  }

  private static final String EXPRESSION = "expression";
  private static final String PRIMARY = "primary";
  private static final String OPEN = "'('";
  private static final String CLOSE = "')'";
  private static final String TYPE_TYPE = "typeType";
  private static final String PLUS = "'+'";
  private static final String TIMES = "'*'";
  private static final String DIVIDE = "'/'";
  private static final String LONG = "long";
  private static final String TWO = "2";
  private static final int BINARY_PARTS = 3;
  private static final int CAST_PARTS = 4;
  private static final int PAREN_PARTS = 3;
  private static final int CAST_OPERAND = 3;
  private static final int RIGHT = 2;
  private static final int DECLARATION_DEPTH = 4;
  private static final String VARIABLE_INITIALIZER = "variableInitializer";
  private static final Set<String> INTEGRAL = Set.of("int", "short", "byte", "char");
  private static final Set<String> FLOATING = Set.of("double", "float");
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check looks for
   * @param message the message reported at the expression
   */
  public IntegerArithmeticCheck(final Mode mode, final String message) {
    super(NEED);
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final SyntaxNode node = visit.node();
      if (reported(visit, types)) {
        matches.add(CheckSupport.at(node.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean reported(final SyntaxWalk.Visit visit, final UnitTypeFacts types) {
    final SyntaxNode node = visit.node();
    return switch (mode) {
      case DIVISION_CAST_TO_FLOATING ->
          castTo(node, FLOATING).filter(operand -> integral(operand, DIVIDE, types)).isPresent();
      case MULTIPLICATION_WIDENED ->
          castTo(node, Set.of(LONG)).filter(operand -> integral(operand, TIMES, types)).isPresent()
              || storedInLong(visit, types);
      default -> average(node, types);
    };
  }

  /** Returns the operand of a cast to one of the given primitive types, without parentheses. */
  private static Optional<SyntaxNode> castTo(final SyntaxNode node, final Set<String> targets) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() != CAST_PARTS
        || !OPEN.equals(parts.get(0).kind())
        || !TYPE_TYPE.equals(parts.get(1).kind())
        || !targets.contains(SyntaxWalk.text(parts.get(1)))) {
      return Optional.empty();
    }
    return Optional.of(unwrap(parts.get(CAST_OPERAND)));
  }

  /** Tells whether the product is the initializer of a {@code long} variable. */
  private static boolean storedInLong(final SyntaxWalk.Visit visit, final UnitTypeFacts types) {
    final List<SyntaxNode> up = visit.ancestors();
    return up.size() > DECLARATION_DEPTH
        && VARIABLE_INITIALIZER.equals(up.get(up.size() - 1).kind())
        && integral(visit.node(), TIMES, types)
        && up.get(up.size() - DECLARATION_DEPTH).children().stream()
            .anyMatch(
                child -> TYPE_TYPE.equals(child.kind()) && LONG.equals(SyntaxWalk.text(child)));
  }

  private boolean average(final SyntaxNode node, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = node.children();
    return parts.size() == BINARY_PARTS
        && DIVIDE.equals(parts.get(1).kind())
        && TWO.equals(SyntaxWalk.text(parts.get(RIGHT)))
        && integral(unwrap(parts.get(0)), PLUS, types)
        && notLiteral(unwrap(parts.get(0)));
  }

  private static boolean notLiteral(final SyntaxNode sum) {
    return sum.children().stream()
        .filter(child -> EXPRESSION.equals(child.kind()))
        .noneMatch(child -> Character.isDigit(SyntaxWalk.text(child).charAt(0)));
  }

  /** Tells whether {@code node} is {@code left operator right} over two integral operands. */
  private static boolean integral(
      final SyntaxNode node, final String operator, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = node.children();
    return parts.size() == BINARY_PARTS
        && operator.equals(parts.get(1).kind())
        && integral(parts.get(0), parts.get(RIGHT), types);
  }

  private static boolean integral(
      final SyntaxNode left, final SyntaxNode right, final UnitTypeFacts types) {
    return INTEGRAL.contains(primitive(left, types)) && INTEGRAL.contains(primitive(right, types));
  }

  private static String primitive(final SyntaxNode operand, final UnitTypeFacts types) {
    return types
        .typeOf(operand.span())
        .filter(TypeRef::primitive)
        .filter(type -> type.arrayDepth() == 0)
        .map(TypeRef::name)
        .orElse("");
  }

  /** Removes the parentheses around an expression. */
  private static SyntaxNode unwrap(final SyntaxNode node) {
    final List<SyntaxNode> inside = node.children();
    if (inside.size() == 1 && PRIMARY.equals(inside.get(0).kind())) {
      final List<SyntaxNode> parts = inside.get(0).children();
      if (parts.size() == PAREN_PARTS
          && OPEN.equals(parts.get(0).kind())
          && CLOSE.equals(parts.get(RIGHT).kind())) {
        return unwrap(parts.get(1));
      }
    }
    return node;
  }
}
