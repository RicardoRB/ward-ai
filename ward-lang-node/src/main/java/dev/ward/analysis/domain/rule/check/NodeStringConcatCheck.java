package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reports each outermost {@code +} chain that has a string literal and at least one other operand,
 * like ESLint's prefer-template. A chain of literals only, or numbers added to variables, is not
 * reported. Parenthesized operands belong to the chain they sit in.
 */
public final class NodeStringConcatCheck implements RuleCheck {

  private static final String EXPRESSION = "singleExpression";
  private static final String PLUS = "'+'";
  private static final String OPEN = "'('";
  private static final String CLOSE = "')'";
  private static final int BINARY_PARTS = 3;
  private static final int OPERAND = 2;
  private static final String SEQUENCE = "expressionSequence";
  private static final Pattern STRING =
      Pattern.compile("'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\"|`[^`]*`");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeStringConcatCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    visit(root, found);
    return List.copyOf(found);
  }

  private void visit(final SyntaxNode node, final List<RuleMatch> found) {
    if (!concatenation(node)) {
      node.children().forEach(child -> visit(child, found));
      return;
    }
    final List<SyntaxNode> operands = new ArrayList<>();
    operands(node, operands);
    if (mixed(operands)) {
      found.add(new RuleMatch(node.span(), message));
    }
    operands.forEach(operand -> visit(operand, found));
  }

  private static void operands(final SyntaxNode node, final List<SyntaxNode> out) {
    if (concatenation(node)) {
      final List<SyntaxNode> parts = NodeSyntax.code(node);
      operands(parts.get(0), out);
      operands(parts.get(OPERAND), out);
      return;
    }
    final Optional<SyntaxNode> inner = unwrap(node);
    if (inner.isPresent()) {
      operands(inner.get(), out);
    } else {
      out.add(node);
    }
  }

  private static boolean concatenation(final SyntaxNode node) {
    if (!EXPRESSION.equals(node.kind())) {
      return false;
    }
    final List<SyntaxNode> parts = NodeSyntax.code(node);
    return parts.size() == BINARY_PARTS
        && PLUS.equals(parts.get(1).kind())
        && EXPRESSION.equals(parts.get(0).kind())
        && EXPRESSION.equals(parts.get(2).kind());
  }

  /** The expression inside a parenthesized operand, or the node itself when it is not one. */
  private static Optional<SyntaxNode> unwrap(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeSyntax.code(node);
    if (!EXPRESSION.equals(node.kind())
        || parts.size() != BINARY_PARTS
        || !OPEN.equals(parts.get(0).kind())
        || !CLOSE.equals(parts.get(2).kind())
        || !SEQUENCE.equals(parts.get(1).kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> inside = NodeSyntax.code(parts.get(1));
    return inside.size() == 1 && EXPRESSION.equals(inside.get(0).kind())
        ? Optional.of(inside.get(0))
        : Optional.empty();
  }

  private static boolean mixed(final List<SyntaxNode> operands) {
    final boolean string = operands.stream().anyMatch(NodeStringConcatCheck::stringLiteral);
    final boolean other = operands.stream().anyMatch(operand -> !stringLiteral(operand));
    return string && other;
  }

  private static boolean stringLiteral(final SyntaxNode operand) {
    return STRING.matcher(NodeSyntax.text(operand)).matches();
  }
}
