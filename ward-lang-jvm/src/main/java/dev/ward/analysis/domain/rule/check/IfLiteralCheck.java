package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a literal that is a direct operand of the whole condition of an {@code if}, as PMD's
 * {@code AvoidLiteralsInIfCondition} does by default: {@code count > 3} is reported, {@code a && b
 * == 3} and {@code size + 1} are not. {@code null}, booleans and the listed numbers are ignored; a
 * negative number is a unary expression, so it is never reported.
 *
 * <p>shortcut: PMD's {@code ignoreExpressions = false} mode, which also reports nested literals, is
 * not offered.
 */
public final class IfLiteralCheck implements UnitCheck {
  private static final String STATEMENT = "statement";
  private static final String IF = "'if'";
  private static final String EXPRESSION = "expression";
  private static final String PRIMARY = "primary";
  private static final String LITERAL = "literal";
  private static final Set<String> NOT_MAGIC = Set.of("null", "true", "false");
  private static final int SINGLE = 1;
  private static final int CONDITION = 2;
  private static final int PARENTHESIZED = 3;

  private final Set<String> ignored;
  private final String message;

  /**
   * Creates the check.
   *
   * @param ignored the literal texts that are not reported, such as {@code 0}
   * @param message the message; {@code {value}} is the literal text
   */
  public IfLiteralCheck(final List<String> ignored, final String message) {
    this.ignored = Set.copyOf(ignored);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(STATEMENT))) {
      final SyntaxNode statement = visit.node();
      if (statement.children().size() > CONDITION
          && IF.equals(statement.children().get(0).kind())) {
        operands(statement.children().get(CONDITION), found);
      }
    }
    return List.copyOf(found);
  }

  private void operands(final SyntaxNode condition, final List<RuleMatch> found) {
    final SyntaxNode top = unwrap(condition);
    for (final SyntaxNode operand : top.children()) {
      literal(operand)
          .filter(this::magic)
          .ifPresent(
              node ->
                  found.add(
                      CheckSupport.at(
                          node.span(), CheckSupport.fill(message, SyntaxWalk.text(node), ""))));
    }
  }

  private boolean magic(final SyntaxNode literal) {
    final String text = SyntaxWalk.text(literal);
    return !NOT_MAGIC.contains(text) && !ignored.contains(text);
  }

  /** Steps into the expression of a parenthesised expression, which PMD does not represent. */
  private static SyntaxNode unwrap(final SyntaxNode expression) {
    SyntaxNode current = expression;
    Optional<SyntaxNode> inner = parenthesized(current);
    while (inner.isPresent()) {
      current = inner.get();
      inner = parenthesized(current);
    }
    return current;
  }

  private static Optional<SyntaxNode> parenthesized(final SyntaxNode expression) {
    if (!EXPRESSION.equals(expression.kind()) || expression.children().size() != SINGLE) {
      return Optional.empty();
    }
    final SyntaxNode primary = expression.children().get(0);
    return PRIMARY.equals(primary.kind()) && primary.children().size() == PARENTHESIZED
        ? Optional.of(primary.children().get(1))
        : Optional.empty();
  }

  private static Optional<SyntaxNode> literal(final SyntaxNode operand) {
    final SyntaxNode inner = unwrap(operand);
    if (!EXPRESSION.equals(inner.kind()) || inner.children().size() != SINGLE) {
      return Optional.empty();
    }
    final SyntaxNode primary = inner.children().get(0);
    return PRIMARY.equals(primary.kind())
            && primary.children().size() == SINGLE
            && LITERAL.equals(primary.children().get(0).kind())
        ? Optional.of(primary.children().get(0))
        : Optional.empty();
  }
}
