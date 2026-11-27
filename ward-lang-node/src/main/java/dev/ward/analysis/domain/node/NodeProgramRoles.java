package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/**
 * Says what a parent node does with the value of one of its children: passes it on, reads it into
 * a number or a string, or lets it change.
 */
final class NodeProgramRoles {
  private static final String ARGUMENTS = "arguments";
  private static final String OPEN_PAREN = "(";
  private static final String OPEN_BRACKET = "[";
  private static final String NOT_NULL = "!";
  private static final String PLUS_PLUS = "++";
  private static final String MINUS_MINUS = "--";
  private static final String AWAIT = "await";
  private static final String DELETE = "delete";
  private static final String ITERATION = "iterationStatement";
  private static final Set<String> PASSING = Set.of("??", "||", "&&", "as", "satisfies");
  private static final Set<String> UNARY_READS = Set.of("typeof", "void", NOT_NULL, "-", "+", "~");
  private static final Set<String> COMPARISONS = Set.of("==", "!=", "===", "!==", "<=", ">=");
  private static final Set<String> CONDITIONS =
      Set.of(
          "ifStatement",
          "whileStatement",
          "doStatement",
          "expressionStatement",
          "templateStringAtom"
      );
  private static final int UNARY_PARTS = 2;
  private static final int BINARY_PARTS = 3;
  private static final int INDEX_PARTS = 4;
  private static final int TERNARY_PARTS = 5;

  /** What a parent node does with the value of one of its children. */
  enum Role {
    /** The parent reads a member of the value; its result may be a part of the value. */
    MEMBER,
    /** The parent's own value can be the value itself. */
    PASS,
    /** The parent turns the value into a string, a number or a boolean, or drops it. */
    CONSUMED,
    /** The parent calls the value, or a member of it, as a function. */
    CALLEE,
    /** The parent hands the value to a call as an argument. */
    ARGUMENT,
    /** The parent declares a name with the value. */
    INITIALIZER,
    /** The parent loops over the value and binds each element. */
    ITERABLE,
    /** The parent stores the value as an element or a property of a literal. */
    ELEMENT,
    /** The parent assigns to the value, deletes it or counts it up or down. */
    MUTATION,
    /** The parent sends the value where the analysis does not follow it. */
    REJECT
  }

  private NodeProgramRoles() {}

  /** What the parent {@code parent} does with the value of its child {@code child}. */
  static Role roleOf(final SyntaxNode parent, final SyntaxNode child) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(parent);
    return switch (parent.kind()) {
      case NodeProgramShapes.SINGLE -> NodeProgramExpressionRoles.of(parts, child);
      case "expressionSequence" -> parts.size() == 1 ? Role.PASS : Role.REJECT;
      case "argument" -> unlessSpread(parent, Role.ARGUMENT);
      case "variableDeclaration" -> declarationRole(parts, child);
      case "arrayElement" -> unlessSpread(parent, Role.ELEMENT);
      case "propertyAssignment" -> propertyRole(parts, child);
      case ITERATION -> loopRole(parts, child);
      default -> CONDITIONS.contains(parent.kind()) ? Role.CONSUMED : Role.REJECT;
    };
  }

  private static Role unlessSpread(final SyntaxNode parent, final Role role) {
    return NodeProgramShapes.isSpread(parent) ? Role.REJECT : role;
  }

  private static Role propertyRole(final List<SyntaxNode> parts, final SyntaxNode child) {
    return parts.get(parts.size() - 1) == child ? Role.ELEMENT : Role.REJECT;
  }

  private static Role declarationRole(final List<SyntaxNode> parts, final SyntaxNode child) {
    final int last = parts.size() - 1;
    final boolean initializer =
        last >= 1 && parts.get(last) == child && "=".equals(
            NodeFlowSyntax.text(parts.get(last - 1))
        );
    return initializer ? Role.INITIALIZER : Role.REJECT;
  }

  private static Role loopRole(final List<SyntaxNode> parts, final SyntaxNode child) {
    final int of = NodeProgramShapes.indexOfToken(parts, "of");
    if (of < 0) {
      return Role.CONSUMED;
    }
    return parts.indexOf(child) > of ? Role.ITERABLE : Role.REJECT;
  }
}
