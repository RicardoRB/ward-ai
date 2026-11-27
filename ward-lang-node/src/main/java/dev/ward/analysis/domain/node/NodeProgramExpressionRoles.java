package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeProgramRoles.Role;
import java.util.List;
import java.util.Set;

/** Says what an expression does with the value of one of its parts. */
final class NodeProgramExpressionRoles {
  private static final String ARGUMENTS = "arguments";
  private static final String OPEN_PAREN = "(";
  private static final String OPEN_BRACKET = "[";
  private static final String NOT_NULL = "!";
  private static final String PLUS_PLUS = "++";
  private static final String MINUS_MINUS = "--";
  private static final String AWAIT = "await";
  private static final String DELETE = "delete";
  private static final Set<String> PASSING = Set.of("??", "||", "&&", "as", "satisfies");
  private static final Set<String> UNARY_READS = Set.of("typeof", "void", NOT_NULL, "-", "+", "~");
  private static final Set<String> COMPARISONS = Set.of("==", "!=", "===", "!==", "<=", ">=");
  private static final int UNARY_PARTS = 2;
  private static final int BINARY_PARTS = 3;
  private static final int INDEX_PARTS = 4;
  private static final int TERNARY_PARTS = 5;

  private NodeProgramExpressionRoles() {}

  static Role of(final List<SyntaxNode> parts, final SyntaxNode child) {
    return switch (parts.size()) {
      case UNARY_PARTS -> parts.get(0) == child ? suffixRole(parts) : prefixRole(parts);
      case BINARY_PARTS -> binaryRole(parts, child);
      case INDEX_PARTS -> indexRole(parts, child);
      case TERNARY_PARTS -> parts.get(0) == child ? Role.CONSUMED : Role.PASS;
      default -> Role.REJECT;
    };
  }

  private static Role indexRole(final List<SyntaxNode> parts, final SyntaxNode child) {
    final boolean object =
        parts.get(0) == child && OPEN_BRACKET.equals(NodeFlowSyntax.text(parts.get(1)));
    return object ? Role.MEMBER : Role.CONSUMED;
  }

  /** The child is followed by an argument list or a postfix operator. */
  private static Role suffixRole(final List<SyntaxNode> parts) {
    final SyntaxNode suffix = parts.get(1);
    if (ARGUMENTS.equals(suffix.kind()) || NodeCallShapes.isParenthesized(suffix)) {
      return Role.CALLEE;
    }
    final String text = NodeFlowSyntax.text(suffix);
    if (PLUS_PLUS.equals(text) || MINUS_MINUS.equals(text)) {
      return Role.MUTATION;
    }
    return NOT_NULL.equals(text) ? Role.PASS : Role.REJECT;
  }

  /** The child follows a prefix operator. */
  private static Role prefixRole(final List<SyntaxNode> parts) {
    final String operator = NodeFlowSyntax.text(parts.get(0));
    if (AWAIT.equals(operator)) {
      return Role.PASS;
    }
    if (DELETE.equals(operator) || PLUS_PLUS.equals(operator) || MINUS_MINUS.equals(operator)) {
      return Role.MUTATION;
    }
    return UNARY_READS.contains(operator) ? Role.CONSUMED : Role.REJECT;
  }

  private static Role binaryRole(final List<SyntaxNode> parts, final SyntaxNode child) {
    final String operator = NodeFlowSyntax.text(parts.get(1));
    if (OPEN_PAREN.equals(NodeFlowSyntax.text(parts.get(0)))) {
      return Role.PASS;
    }
    if (isAssignment(operator)) {
      return parts.get(0) == child ? Role.MUTATION : Role.REJECT;
    }
    if (NodeProgramShapes.MEMBER_OPERATORS.contains(operator)) {
      return parts.get(0) == child ? Role.MEMBER : Role.REJECT;
    }
    return PASSING.contains(operator) ? Role.PASS : Role.CONSUMED;
  }

  private static boolean isAssignment(final String operator) {
    return operator.endsWith("=") && !COMPARISONS.contains(operator);
  }
}
