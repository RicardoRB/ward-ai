package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an {@code else if} condition that can never be reached, like ESLint {@code
 * no-dupe-else-if}: a condition equal to an earlier one of its chain, or covered by them. A failed
 * earlier condition makes the atoms of a {@code ||} false; a {@code &&} is covered when one of its
 * operands is.
 */
public final class NodeDupeElseIfCheck implements RuleCheck {

  private static final String IF = "ifStatement";
  private static final String STATEMENT = "statement";
  private static final String TEST = "expressionSequence";
  private static final String SINGLE = "singleExpression";
  private static final String ELSE = "else";
  private static final String AND = "&&";
  private static final String OR = "||";
  private static final String OPEN = "(";
  private static final String CLOSE = ")";
  private static final int PARENTHESIZED = 3;
  private static final int BINARY = 3;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeDupeElseIfCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found, Collections.newSetFromMap(new IdentityHashMap<>()));
    return List.copyOf(found);
  }

  private void collect(
      final SyntaxNode node, final List<RuleMatch> found, final Set<SyntaxNode> chained) {
    if (IF.equals(node.kind()) && !chained.contains(node)) {
      chain(node, found, chained);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, found, chained);
    }
  }

  private void chain(
      final SyntaxNode first, final List<RuleMatch> found, final Set<SyntaxNode> chained) {
    final Set<String> facts = new HashSet<>();
    SyntaxNode current = first;
    boolean firstCondition = true;
    while (current != null) {
      final Optional<SyntaxNode> test = expressionOf(current);
      if (test.isPresent()) {
        if (!firstCondition && covered(test.get(), facts)) {
          found.add(new RuleMatch(test.get().span(), message));
        }
        addFacts(test.get(), facts);
      }
      final Optional<SyntaxNode> next = elseIf(current);
      next.ifPresent(chained::add);
      current = next.orElse(null);
      firstCondition = false;
    }
  }

  /** The condition of an if statement: its expression sequence, whose one expression it holds. */
  private static Optional<SyntaxNode> expressionOf(final SyntaxNode ifNode) {
    return ifNode.children().stream()
        .filter(child -> TEST.equals(child.kind()))
        .findFirst()
        .flatMap(
            sequence ->
                sequence.children().stream().filter(c -> SINGLE.equals(c.kind())).findFirst());
  }

  /** The if statement of an else branch, when the branch is {@code else if}. */
  private static Optional<SyntaxNode> elseIf(final SyntaxNode ifNode) {
    final List<SyntaxNode> children = ifNode.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (ELSE.equals(LeafText.of(children.get(index)))) {
        final SyntaxNode branch = children.get(index + 1);
        return STATEMENT.equals(branch.kind())
            ? branch.children().stream().filter(c -> IF.equals(c.kind())).findFirst()
            : Optional.empty();
      }
    }
    return Optional.empty();
  }

  private static boolean covered(final SyntaxNode expr, final Set<String> facts) {
    final SyntaxNode node = unwrap(expr);
    if (facts.contains(LeafText.of(node))) {
      return true;
    }
    final Optional<SyntaxNode[]> logical = logical(node);
    if (logical.isEmpty()) {
      return false;
    }
    final SyntaxNode[] operands = logical.get();
    final boolean left = covered(operands[0], facts);
    final boolean right = covered(operands[2], facts);
    return "&&".equals(LeafText.of(operands[1])) ? left || right : left && right;
  }

  private static void addFacts(final SyntaxNode expr, final Set<String> facts) {
    final SyntaxNode node = unwrap(expr);
    facts.add(LeafText.of(node));
    final Optional<SyntaxNode[]> logical = logical(node);
    if (logical.isPresent() && OR.equals(LeafText.of(logical.get()[1]))) {
      addFacts(logical.get()[0], facts);
      addFacts(logical.get()[2], facts);
    }
  }

  /** The operands and the operator of a binary logical expression. */
  private static Optional<SyntaxNode[]> logical(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    if (children.size() != BINARY) {
      return Optional.empty();
    }
    final String operator = LeafText.of(children.get(1));
    return AND.equals(operator) || OR.equals(operator)
        ? Optional.of(new SyntaxNode[] {children.get(0), children.get(1), children.get(2)})
        : Optional.empty();
  }

  /** A parenthesized expression stands for its inner expression. */
  private static SyntaxNode unwrap(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    if (children.size() == PARENTHESIZED
        && OPEN.equals(LeafText.of(children.get(0)))
        && CLOSE.equals(LeafText.of(children.get(2)))) {
      return unwrap(children.get(1));
    }
    if (TEST.equals(node.kind()) && children.size() == 1) {
      return unwrap(children.get(0));
    }
    return node;
  }
}
