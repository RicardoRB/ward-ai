package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.NodeMagicNumberSyntax.isAssign;
import static dev.ward.analysis.domain.rule.check.NodeMagicNumberSyntax.isParseIntCall;
import static dev.ward.analysis.domain.rule.check.NodeMagicNumberSyntax.isToken;
import static dev.ward.analysis.domain.rule.check.NodeMagicNumberSyntax.tokenBefore;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.check.NodeMagicNumberRoles.Role;
import java.util.List;
import java.util.Map;

/**
 * The roles of an expression under a parent that holds it in a grammar node of its own: a class
 * field, a default value, an assignment, an array index, a call argument or a JSX expression.
 */
final class NodeMagicNumberContext {

  private static final String PARAMETER_ARGUMENT = "formalParameterArg";
  private static final String PROPERTY_ASSIGNMENT = "propertyAssignment";
  private static final String LITERAL_EXPRESSION = "singleExpression";
  private static final String PARAMETER = "optionalParameter";
  private static final String CLASS_FIELD = "propertyMemberDeclaration";
  private static final String FIELD = "fieldDefinition";
  private static final String SEQUENCE = "expressionSequence";
  private static final String ARGUMENT = "argument";
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String JSX_CONTAINER = "jsxExpressionContainer";
  private static final String OPEN_BRACKET = "[";

  private final Map<SyntaxNode, SyntaxNode> parents;

  NodeMagicNumberContext(final Map<SyntaxNode, SyntaxNode> parents) {
    this.parents = parents;
  }

  /** The role of the expression under a parent that is not one of the expression kinds. */
  Role wrapperRole(final SyntaxNode node, final SyntaxNode parent) {
    return switch (parent.kind()) {
      case PARAMETER_ARGUMENT -> isAssign(tokenBefore(parent, node)) ? Role.DEFAULT : Role.OTHER;
      case SEQUENCE -> indexRole(parent);
      case ARGUMENT -> argumentRole(parent);
      case JSX_CONTAINER -> Role.JSX;
      default -> Role.OTHER;
    };
  }

  /** The role of an initializer: a default parameter or a class field. */
  Role initializerRole(final SyntaxNode initializer) {
    final SyntaxNode owner = parents.get(initializer);
    if (owner == null) {
      return Role.OTHER;
    }
    if (PARAMETER.equals(owner.kind())) {
      return Role.DEFAULT;
    }
    return CLASS_FIELD.equals(owner.kind()) || FIELD.equals(owner.kind())
        ? Role.CLASS_FIELD
        : Role.OTHER;
  }

  /** The right side of an assignment; a target of a member is the object form. */
  Role assignmentRole(final SyntaxNode node, final SyntaxNode expression) {
    if (!isAssign(tokenBefore(expression, node))) {
      return Role.OTHER;
    }
    final SyntaxNode owner = parents.get(expression);
    if (owner != null && PROPERTY_ASSIGNMENT.equals(owner.kind())) {
      return Role.DEFAULT;
    }
    final SyntaxNode target = expression.children().get(0);
    return NodeSyntax.leaves(target).size() == 1 ? Role.OTHER : Role.ASSIGNED_MEMBER;
  }

  /** The index of a member access, which the sequence of the bracket holds. */
  private Role indexRole(final SyntaxNode sequence) {
    final SyntaxNode access = parents.get(sequence);
    if (access == null || !LITERAL_EXPRESSION.equals(access.kind())) {
      return Role.OTHER;
    }
    return access.children().stream().anyMatch(part -> isToken(part, OPEN_BRACKET))
        ? Role.MEMBER_INDEX
        : Role.OTHER;
  }

  /**
   * The second argument of {@code parseInt} or {@code Number.parseInt}, the radix. The TypeScript
   * grammar groups the arguments in an argument list under the call's arguments.
   */
  private Role argumentRole(final SyntaxNode argument) {
    final SyntaxNode list = parents.get(argument);
    if (list == null) {
      return Role.OTHER;
    }
    final SyntaxNode arguments = ARGUMENT_LIST.equals(list.kind()) ? parents.get(list) : list;
    final SyntaxNode call = arguments == null ? null : parents.get(arguments);
    if (call == null || !ARGUMENTS.equals(arguments.kind())) {
      return Role.OTHER;
    }
    final List<SyntaxNode> values =
        list.children().stream().filter(part -> ARGUMENT.equals(part.kind())).toList();
    final boolean radix =
        values.size() > 1 && values.get(1).equals(argument) && isParseIntCall(call);
    return radix ? Role.RADIX : Role.OTHER;
  }
}
