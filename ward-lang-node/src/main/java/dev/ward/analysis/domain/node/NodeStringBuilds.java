package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shapes of a string built from literal and value parts: a chain of {@code +} with a string
 * literal, a template literal with substitutions, and {@code .concat} on a string literal.
 */
final class NodeStringBuilds {

  private static final String PLUS = "+";
  private static final String DOT = ".";
  private static final String CONCAT = "concat";
  private static final String LITERAL = "literal";
  private static final String TEMPLATE = "templateStringLiteral";
  private static final String TEMPLATE_ATOM = "templateStringAtom";
  private static final String SINGLE = "singleExpression";
  private static final String BACKTICK = "`";
  private static final String SUBSTITUTION_OPEN = "${";
  private static final Pattern QUOTED = Pattern.compile("(['\"])([\\s\\S]*)\\1");
  private static final int CONTENT_GROUP = 2;
  private static final int PARENTHESIZED_PARTS = 3;
  private static final int CHAIN_PARTS = 3;
  private static final int MEMBER_PARTS = 3;

  private NodeStringBuilds() {}

  /** Reports whether the parts are a binary {@code +}: an operand, the operator and an operand. */
  static boolean isChain(final List<SyntaxNode> parts) {
    return parts.size() == CHAIN_PARTS && PLUS.equals(NodeFlowSyntax.text(parts.get(1)));
  }

  /**
   * The operands of a chain of {@code +}. A parenthesized chain joins the outer one, so its
   * operands are listed too; any other node is its own operand.
   */
  static List<SyntaxNode> operands(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    final List<SyntaxNode> operands = new ArrayList<>();
    if (isChain(parts)) {
      operands.addAll(operands(parts.get(0)));
      operands.addAll(operands(parts.get(2)));
    } else if (isParenthesized(parts)) {
      operands.addAll(operands(parts.get(1)));
    } else {
      operands.add(node);
    }
    return operands;
  }

  private static boolean isParenthesized(final List<SyntaxNode> parts) {
    return parts.size() == PARENTHESIZED_PARTS
        && "(".equals(NodeFlowSyntax.text(parts.get(0)))
        && ")".equals(NodeFlowSyntax.text(parts.get(2)));
  }

  /** The text of a string literal or a template without substitutions, without its quotes. */
  static Optional<String> literalText(final SyntaxNode operand) {
    final String text = NodeFlowSyntax.text(operand);
    final Matcher quoted = QUOTED.matcher(text);
    if (quoted.matches()) {
      return Optional.of(quoted.group(CONTENT_GROUP));
    }
    final boolean template =
        text.length() >= 2 && text.startsWith(BACKTICK) && text.endsWith(BACKTICK);
    return template && !text.contains(SUBSTITUTION_OPEN)
        ? Optional.of(text.substring(1, text.length() - 1))
        : Optional.empty();
  }

  /**
   * The receiver of a callee that is {@code literal.concat}, when the receiver is a string literal.
   *
   * @param calleeParts the parts of the callee
   * @return the literal receiver, when the callee is {@code concat} on one
   */
  static Optional<SyntaxNode> concatReceiver(final List<SyntaxNode> calleeParts) {
    if (calleeParts.size() != MEMBER_PARTS
        || !DOT.equals(NodeFlowSyntax.text(calleeParts.get(1)))
        || !CONCAT.equals(NodeFlowSyntax.text(calleeParts.get(2)))
        || literalText(calleeParts.get(0)).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(calleeParts.get(0));
  }

  /** The template literal of a singleExpression that is one, when it is one. */
  static Optional<SyntaxNode> templateOf(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (parts.size() != 1 || !LITERAL.equals(parts.get(0).kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> children = parts.get(0).children();
    return children.size() == 1 && TEMPLATE.equals(children.get(0).kind())
        ? Optional.of(children.get(0))
        : Optional.empty();
  }

  /** The substitution expressions of a template literal, in source order. */
  static List<SyntaxNode> substitutions(final SyntaxNode template) {
    final List<SyntaxNode> values = new ArrayList<>();
    for (final SyntaxNode atom : template.children()) {
      substitutionOf(atom).ifPresent(values::add);
    }
    return values;
  }

  /** The literal text of a template: its atoms that are not substitutions, joined in order. */
  static String templateText(final SyntaxNode template) {
    final StringBuilder text = new StringBuilder();
    for (final SyntaxNode atom : template.children()) {
      if (TEMPLATE_ATOM.equals(atom.kind()) && substitutionOf(atom).isEmpty()) {
        text.append(NodeFlowSyntax.text(atom));
      }
    }
    return text.toString();
  }

  private static Optional<SyntaxNode> substitutionOf(final SyntaxNode atom) {
    for (final SyntaxNode child : atom.children()) {
      if (SINGLE.equals(child.kind())) {
        return Optional.of(child);
      }
    }
    return Optional.empty();
  }
}
