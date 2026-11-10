package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shapes of the TypeScript and JavaScript expressions that member writes and constructors use.
 */
final class NodeCallShapes {

  private static final String NEW = "new";
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT = "argument";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String EXPRESSION_SEQUENCE = "expressionSequence";
  private static final int NEW_PARTS = 3;
  private static final int KEY_PARTS = 4;
  private static final int KEY_OPEN = 1;
  private static final int KEY_EXPRESSION = 2;
  private static final int KEY_CLOSE = 3;
  private static final int KEY_NAME_GROUP = 2;
  private static final Pattern STRING_KEY = Pattern.compile("(['\"])([\\w$]+)\\1");

  private NodeCallShapes() {}

  /**
   * The property of an assignment target: the name of {@code a.b}, or the key of {@code a['b']}.
   */
  static Optional<String> writtenName(final List<SyntaxNode> target) {
    if (NodeFlowSites.isMember(target)) {
      return Optional.of(NodeFlowSyntax.text(target.get(target.size() - 1)));
    }
    if (target.size() != KEY_PARTS
        || !"[".equals(NodeFlowSyntax.text(target.get(KEY_OPEN)))
        || !"]".equals(NodeFlowSyntax.text(target.get(KEY_CLOSE)))) {
      return Optional.empty();
    }
    final Matcher key = STRING_KEY.matcher(NodeFlowSyntax.text(target.get(KEY_EXPRESSION)));
    return key.matches() ? Optional.of(key.group(KEY_NAME_GROUP)) : Optional.empty();
  }

  static boolean isNew(final List<SyntaxNode> parts) {
    return parts.size() == NEW_PARTS
        && NEW.equals(NodeFlowSyntax.text(parts.get(0)))
        && ARGUMENTS.equals(parts.get(2).kind());
  }

  /**
   * The grammar gives a call in an initializer or an assignment value a parenthesized expression as
   * its second part, not an arguments node.
   */
  static boolean isParenthesized(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    return parts.size() >= 2
        && "(".equals(NodeFlowSyntax.text(parts.get(0)))
        && ")".equals(NodeFlowSyntax.text(parts.get(parts.size() - 1)));
  }

  static List<SyntaxNode> parenthesizedValues(final SyntaxNode node) {
    final SyntaxNode sequence =
        NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(node), EXPRESSION_SEQUENCE);
    if (sequence == null) {
      return List.of();
    }
    return sequence.children().stream().filter(child -> !child.children().isEmpty()).toList();
  }

  /**
   * The argument nodes of a call, which sit directly under the arguments or in an argument list.
   */
  static List<SyntaxNode> argumentValues(final SyntaxNode arguments) {
    final List<SyntaxNode> values = new ArrayList<>();
    collectArguments(arguments, values);
    return values;
  }

  private static void collectArguments(final SyntaxNode node, final List<SyntaxNode> values) {
    for (final SyntaxNode child : node.children()) {
      if (ARGUMENT.equals(child.kind())) {
        values.add(child);
      } else if (ARGUMENT_LIST.equals(child.kind())) {
        collectArguments(child, values);
      }
    }
  }

  static List<Span> spans(final List<SyntaxNode> nodes) {
    return nodes.stream().map(SyntaxNode::span).toList();
  }
}
