package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a label on a {@code break} or {@code continue} whose target is the innermost loop or
 * switch, which the same jump reaches without the label, like ESLint {@code no-extra-label}.
 */
public final class NodeExtraLabelCheck implements RuleCheck {

  private static final String LABELLED = "labelledStatement";
  private static final String STATEMENT = "statement";
  private static final String IDENTIFIER = "identifier";
  private static final String BREAK = "breakStatement";
  private static final String CONTINUE = "continueStatement";
  private static final Set<String> BREAKABLE = Set.of("iterationStatement", "switchStatement");

  private final String message;

  /** A breakable statement, or a labelled statement that is not one, with its label. */
  private record Frame(boolean breakable, String label) {}

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeExtraLabelCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    walk(root, null, new ArrayDeque<>(), matches);
    return List.copyOf(matches);
  }

  /** Walks the tree with the stack of enclosing breakable and labelled statements. */
  private void walk(
      final SyntaxNode node,
      final String pending,
      final Deque<Frame> stack,
      final List<RuleMatch> matches) {
    if (LABELLED.equals(node.kind())) {
      labelled(node, stack, matches);
    } else if (BREAKABLE.contains(node.kind())) {
      stack.push(new Frame(true, pending));
      children(node, null, stack, matches);
      stack.pop();
    } else if (BREAK.equals(node.kind()) || CONTINUE.equals(node.kind())) {
      jump(node, stack, matches);
      children(node, null, stack, matches);
    } else if (STATEMENT.equals(node.kind())) {
      children(node, pending, stack, matches);
    } else {
      children(node, null, stack, matches);
    }
  }

  private void labelled(
      final SyntaxNode node, final Deque<Frame> stack, final List<RuleMatch> matches) {
    final Optional<SyntaxNode> label = identifier(node);
    if (label.isEmpty()) {
      children(node, null, stack, matches);
      return;
    }
    final String name = LeafText.of(label.get());
    if (isBreakableBody(node)) {
      children(node, name, stack, matches);
      return;
    }
    stack.push(new Frame(false, name));
    children(node, null, stack, matches);
    stack.pop();
  }

  /** The reported label is the nearest enclosing breakable statement's own label, and only that. */
  private void jump(
      final SyntaxNode node, final Deque<Frame> stack, final List<RuleMatch> matches) {
    final Optional<SyntaxNode> label = identifier(node);
    if (label.isEmpty()) {
      return;
    }
    final String name = LeafText.of(label.get());
    for (final Frame frame : stack) {
      if (frame.breakable() || name.equals(frame.label())) {
        if (frame.breakable() && name.equals(frame.label())) {
          matches.add(new RuleMatch(label.get().span(), message.replace("{value}", name)));
        }
        return;
      }
    }
  }

  private static boolean isBreakableBody(final SyntaxNode labelled) {
    return labelled.children().stream()
        .filter(child -> STATEMENT.equals(child.kind()))
        .flatMap(statement -> statement.children().stream())
        .anyMatch(child -> BREAKABLE.contains(child.kind()));
  }

  private static Optional<SyntaxNode> identifier(final SyntaxNode node) {
    return node.children().stream().filter(child -> IDENTIFIER.equals(child.kind())).findFirst();
  }

  private void children(
      final SyntaxNode node,
      final String pending,
      final Deque<Frame> stack,
      final List<RuleMatch> matches) {
    for (final SyntaxNode child : node.children()) {
      walk(child, pending, stack, matches);
    }
  }
}
