package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the expression shapes that the program-data analysis follows.
 */
final class NodeProgramShapes {
  static final String SINGLE = NodeTemplates.SINGLE;
  static final Set<String> MEMBER_OPERATORS = Set.of(".", "?.");
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT = "argument";
  private static final String SPREAD = "...";
  private static final String OPEN_BRACKET = "[";
  private static final String PARTS_OPEN = "(";
  private static final int UNARY_PARTS = 2;
  private static final int BINARY_PARTS = 3;
  private static final int INDEX_PARTS = 4;
  private static final int RIGHT = 2;

  /** The object and the name of a member read {@code object.name}. */
  record Member(SyntaxNode object, String name) {}

  /** The callee of a call and its arguments, which are {@code argument} nodes. */
  record Call(SyntaxNode callee, List<SyntaxNode> arguments) {}

  private NodeProgramShapes() {}

  /** The name token of an expression that is only a name, such as {@code hint}. */
  static Optional<SyntaxNode> name(final SyntaxNode expression) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    if (!SINGLE.equals(expression.kind())
        || parts.size() != 1
        || !"identifierName".equals(parts.get(0).kind())) {
      return Optional.empty();
    }
    return Optional.ofNullable(NodeFlowSyntax.firstIdentifier(parts.get(0)));
  }

  /** The member read {@code object.name}, with a plain name. */
  static Optional<Member> member(final SyntaxNode expression) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    if (!SINGLE.equals(expression.kind())
        || parts.size() != BINARY_PARTS
        || !MEMBER_OPERATORS.contains(NodeFlowSyntax.text(parts.get(1)))) {
      return Optional.empty();
    }
    return Optional.of(new Member(parts.get(0), NodeFlowSyntax.text(parts.get(RIGHT))));
  }

  /** The object of an index read {@code object[key]}. */
  static Optional<SyntaxNode> indexed(final SyntaxNode expression) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    if (SINGLE.equals(expression.kind())
        && parts.size() == INDEX_PARTS
        && OPEN_BRACKET.equals(NodeFlowSyntax.text(parts.get(1)))) {
      return Optional.of(parts.get(0));
    }
    return Optional.empty();
  }

  /** The call {@code callee(arguments)}. */
  static Optional<Call> call(final SyntaxNode expression) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    if (!SINGLE.equals(expression.kind()) || parts.size() != UNARY_PARTS) {
      return Optional.empty();
    }
    final SyntaxNode second = parts.get(1);
    if (ARGUMENTS.equals(second.kind())) {
      return Optional.of(new Call(parts.get(0), NodeCallShapes.argumentValues(second)));
    }
    return NodeCallShapes.isParenthesized(second) && !PARTS_OPEN.equals(
        NodeFlowSyntax.text(parts.get(0))
    ) ? Optional.of(new Call(parts.get(0), NodeCallShapes.parenthesizedValues(second)))
        : Optional.empty();
  }

  /** Whether an argument is spread, {@code ...values}. */
  static boolean isSpread(final SyntaxNode argument) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(argument);
    return !parts.isEmpty() && SPREAD.equals(NodeFlowSyntax.text(parts.get(0)));
  }

  /** The expression of an argument, or of an expression handed over directly. */
  static SyntaxNode valueOf(final SyntaxNode argument) {
    if (!ARGUMENT.equals(argument.kind())) {
      return argument;
    }
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(argument);
    return parts.isEmpty() ? argument : parts.get(parts.size() - 1);
  }

  /** The element expressions of an array literal, or none when it has a spread. */
  static Optional<List<SyntaxNode>> elements(final SyntaxNode literal) {
    final List<SyntaxNode> values = new ArrayList<>();
    for (final SyntaxNode element : descendants(literal, "arrayElement")) {
      if (isSpread(element) || NodeFlowSyntax.parts(element).isEmpty()) {
        return Optional.empty();
      }
      values.add(NodeFlowSyntax.parts(element).get(0));
    }
    return Optional.of(values);
  }

  static int indexOfToken(final List<SyntaxNode> parts, final String token) {
    for (int index = 0; index < parts.size(); index++) {
      if (token.equals(NodeFlowSyntax.text(parts.get(index))) && parts.get(
          index
      ).children().isEmpty()) {
        return index;
      }
    }
    return -1;
  }

  private static List<SyntaxNode> descendants(final SyntaxNode node, final String kind) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (kind.equals(child.kind())) {
        found.add(child);
      } else if (!"arrayLiteral".equals(child.kind())) {
        found.addAll(descendants(child, kind));
      }
    }
    return found;
  }
}
