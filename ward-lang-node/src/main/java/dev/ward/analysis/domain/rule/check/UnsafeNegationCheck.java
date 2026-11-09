package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a binary expression whose left operand is a negation, like ESLint {@code
 * no-unsafe-negation}: {@code !a in b} and {@code !a instanceof B} always, and the ordering
 * operators when {@code enforceForOrderingRelations} is set. The TypeScript grammar nests {@code
 * in} and {@code instanceof} one level deeper than the JavaScript grammar, so both shapes are read.
 */
public final class UnsafeNegationCheck implements RuleCheck {

  private static final String SINGLE = "singleExpression";
  private static final String NOT = "'!'";
  private static final String IDENTIFIER = "identifierName";
  private static final String RESERVED = "reservedWord";
  private static final String KEYWORD = "keyword";
  private static final Set<String> KEYWORD_OPERATORS = Set.of("'in'", "'instanceof'");
  private static final Set<String> ORDERING = Set.of("'<'", "'>'", "'<='", "'>='");
  private static final String VALUE = "{value}";
  private static final int BINARY_OPERANDS = 3;

  private final Set<String> operators;
  private final String message;

  /**
   * Creates the check.
   *
   * @param enforceOrdering whether the ordering operators are checked too
   * @param message the message; {@code {value}} is the operator
   */
  public UnsafeNegationCheck(final boolean enforceOrdering, final String message) {
    this.operators = enforceOrdering ? allOperators() : KEYWORD_OPERATORS;
    this.message = Objects.requireNonNull(message, "message");
  }

  private static Set<String> allOperators() {
    final Set<String> all = new HashSet<>(KEYWORD_OPERATORS);
    all.addAll(ORDERING);
    return Set.copyOf(all);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (SINGLE.equals(node.kind())) {
      operator(node)
          .ifPresent(
              op ->
                  matches.add(
                      new RuleMatch(
                          node.span(), message.replace(VALUE, op.substring(1, op.length() - 1)))));
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, matches);
    }
  }

  private Optional<String> operator(final SyntaxNode node) {
    return javaScriptShape(node).or(() -> typeScriptShape(node));
  }

  /** {@code a op b} where the left operand is a singleExpression that starts with {@code !}. */
  private Optional<String> javaScriptShape(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    if (children.size() < BINARY_OPERANDS || !isNegation(children.get(0))) {
      return Optional.empty();
    }
    final String kind = children.get(1).kind();
    return operators.contains(kind) ? Optional.of(kind) : Optional.empty();
  }

  private static boolean isNegation(final SyntaxNode left) {
    return SINGLE.equals(left.kind())
        && !left.children().isEmpty()
        && NOT.equals(left.children().get(0).kind());
  }

  /** {@code !a op b} in the TypeScript grammar, where the keyword sits under identifierName. */
  private Optional<String> typeScriptShape(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    if (children.isEmpty() || !NOT.equals(children.get(0).kind())) {
      return Optional.empty();
    }
    for (final SyntaxNode child : children) {
      for (final SyntaxNode grand : child.children()) {
        final Optional<String> keyword = keywordOperator(grand);
        if (keyword.isPresent() && operators.contains(keyword.get())) {
          return keyword;
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<String> keywordOperator(final SyntaxNode node) {
    if (!SINGLE.equals(node.kind())) {
      return Optional.empty();
    }
    for (final SyntaxNode identifier : node.children()) {
      for (final SyntaxNode reserved : identifier.children()) {
        for (final SyntaxNode keyword : reserved.children()) {
          if (IDENTIFIER.equals(identifier.kind())
              && RESERVED.equals(reserved.kind())
              && KEYWORD.equals(keyword.kind())) {
            return keyword.children().stream()
                .map(SyntaxNode::kind)
                .filter(KEYWORD_OPERATORS::contains)
                .findFirst();
          }
        }
      }
    }
    return Optional.empty();
  }
}
